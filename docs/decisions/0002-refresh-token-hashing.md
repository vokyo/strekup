# ADR 0002: Refresh Token Hashing

- Status: Accepted
- Context: Refresh tokens are long-lived credentials (30-day TTL) that can mint new access tokens on demand. If the `refresh_tokens` table is ever exfiltrated, plaintext tokens would let an attacker impersonate every active session until each one organically expires.
- Decision: Store only a SHA-256 hash of the opaque refresh token in `refresh_tokens.token_hash`. The plaintext token exists only in the `HttpOnly` cookie on the client and in transient in-memory state during issuance/rotation. Lookup on `/auth/refresh` hashes the incoming cookie value and queries by the hash column (indexed).
- Consequences:
  - A DB leak yields no replayable tokens — the hashes are pre-image-resistant.
  - Verification stays O(1) and fast on the hot rotation path (SHA-256 is cheap; no BCrypt-class cost needed because the input is 32 bytes of CSPRNG output, not user-chosen entropy).
  - The server **cannot** recover a lost token; the user must re-authenticate. This is correct behaviour for a refresh-rotation design.
- Rationale:
  - **Why SHA-256, not BCrypt?** BCrypt's slow-hash design defends against brute-forcing low-entropy inputs like passwords. Refresh tokens are 32 bytes from a CSPRNG — the brute-force space is 2^256, so the slow-hash property buys nothing and only adds latency to every refresh (which happens on every 15 min of active use).
  - **Why not encrypt instead of hash?** Encryption implies the server ever needs the plaintext back. It doesn't — the verification flow is "hash the cookie, look up the row". One-way is strictly safer.
  - **Why not JWTs for the refresh token too?** JWTs can't be revoked without a server-side blocklist, which defeats the point of rotation. Opaque tokens + DB lookup gives us free revocation (`DELETE` the row) and free lineage tracking for theft detection (see `auth-flow.md` Flow 5).
- Implementation notes:
  - Token generation: `SecureRandom.nextBytes(32)` → base64url encode → cookie value.
  - Column: `token_hash CHAR(64)` (hex of SHA-256), `UNIQUE` index, paired with `user_id`, `replaced_by_token_id` (self-FK for lineage), `expires_at`, `revoked_at`. See `er-diagram.md` for the full schema.
  - On refresh: hash input → `SELECT ... WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > NOW()` → if the row is already revoked and has a `replaced_by_token_id`, the lineage has been reused after rotation — trigger chain revocation (see `auth-flow.md` Flow 5).
