# ADR 0007: JWT Algorithm Choice

- Status: Accepted
- Context: Access tokens are JWTs verified on every `🔒` API request. The choice of signing algorithm affects operational complexity, failure modes, and exposure to known JWT pitfalls (algorithm-confusion attacks, `alg: none` bypass, RSA key management). For a single-service MVP with one trusted signer and one trusted verifier (the same process), the simpler the algorithm, the smaller the blast radius.
- Decision: Use **HS256** (HMAC-SHA-256) for access tokens. The signing secret is loaded from `JWT_SECRET` (≥ 32 random bytes, base64-encoded) and kept in AWS SSM Parameter Store in prod. The JWT parser is configured to **explicitly** require `alg = HS256` — any token presenting a different algorithm (including `none`) is rejected before verification.
- Consequences:
  - One secret, one key store, no key rotation ceremony to design before shipping. Rotation = update SSM + redeploy; access tokens invalidate within 15 minutes (their TTL) and the refresh path still works because refresh tokens are opaque, not signed.
  - Verification is a single HMAC — cheap and predictable, safe to run on every request.
  - We can't hand the verification key to a third party without also handing them signing power. Acceptable: we have no third parties to hand it to.
- Rationale:
  - **Why HS256 over RS256/ES256?** RS256/ES256 split signing from verification (private vs. public key), which is valuable when multiple services need to verify tokens without being trusted to mint them. In StreakUp, exactly one process mints and verifies — the benefit of asymmetric keys is zero, while the cost (key-pair management, JWK rotation, larger tokens, slower verification) is real. If we ever split the API into multiple services that need independent verification, this ADR gets revisited.
  - **Why explicitly pin the algorithm?** The `alg` header is attacker-controlled. Libraries that accept "whatever the header says" have been bitten by two classic attacks: `alg: none` (signature stripped), and `alg: HS256` presented against an RS256 public key (using the public key as an HMAC secret). Pinning to HS256 at the parser — and rejecting anything else before signature check — closes both.
  - **Why 32 bytes of entropy for the secret?** HS256 truncates to SHA-256's 256-bit security; a secret shorter than that undersells the algorithm. 32 bytes from a CSPRNG is the floor.
  - **Why JWTs for access tokens but opaque tokens for refresh?** Access tokens need to be self-contained and stateless (verified per request without DB hit) — that's the only reason to use JWTs. Refresh tokens need to be revocable (see ADR 0002) — opaque + DB lookup delivers revocation cleanly. Using each format where its property matters.
- Implementation notes:
  - Library: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`, 0.12.x. Rejects `alg: none` by default.
  - Pin the algorithm at parse time via a `keyLocator` that inspects the JWS header **before** signature verification and throws for anything other than `HS256`. The `Jwts.parser().require(...)` API checks claims in the JWT *body* and does **not** validate the `alg` header — using it for algorithm pinning is a footgun and must not be relied on.
    ```java
    SecretKey hmacKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

    JwtParser parser = Jwts.parser()
        .keyLocator(header -> {
            String alg = ((JwsHeader) header).getAlgorithm();
            if (!"HS256".equals(alg)) {
                throw new UnsupportedJwtException("Unexpected JWT algorithm: " + alg);
            }
            return hmacKey;
        })
        .clockSkewSeconds(30)
        .build();

    Jws<Claims> jws = parser.parseSignedClaims(token);
    ```
    `JwtServiceTest` asserts that tokens forged with `alg=none`, `alg=RS256`, or `alg=HS384` are rejected with `UnsupportedJwtException` before claims are read.
  - Token claims: `sub = userId`, `iat`, `exp`, `typ = "access"`. No PII in the token — the `typ` claim lets us fail fast if a refresh token is ever presented where an access token is expected.
  - Clock skew tolerance: 30 seconds, applied symmetrically, to absorb minor NTP drift without widening the exposure window meaningfully.
