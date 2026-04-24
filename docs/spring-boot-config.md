# StreakUp — Spring Boot Configuration

> **Audience**: anyone booting or deploying the backend.
> **Scope**: the Spring profile layout, `application-*.yml` templates, the `@ValidTimezone` custom validator signature, and the pinned Testcontainers versions the integration tests rely on.
> **Cross-reference**: env var reference lives in [environment-variables.md](environment-variables.md); this document shows how those variables map into Spring config.

---

## Profile model

| Profile | Activated by | Data sources | Use case |
|---|---|---|---|
| `dev` | `SPRING_PROFILES_ACTIVE=dev` (default in `.env.local.example`) | `application.yml` + `application-dev.yml` + `.env.local` | `./mvnw spring-boot:run` against `docker compose up` infra. |
| `test` | Auto-activated by `@SpringBootTest` / `@ActiveProfiles("test")` | `application.yml` + `application-test.yml` + Testcontainers-minted URLs | JUnit + Testcontainers in CI and locally. |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` in the systemd/Docker env | `application.yml` + `application-prod.yml` + SSM-injected env vars | EC2 deployment. |

Only one profile is ever active at runtime. `application.yml` holds the defaults shared across all three; per-profile files override.

---

## `application.yml` (shared base)

```yaml
spring:
  application:
    name: streakup
  jpa:
    hibernate:
      ddl-auto: validate          # schema owned by Flyway, never Hibernate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC          # persisted timestamps are UTC (coding-standards.md)
        format_sql: false
  flyway:
    enabled: true
    baseline-on-migrate: false    # forward-only; never pretend an existing schema is v0
    locations: classpath:db/migration
  jackson:
    deserialization:
      fail-on-unknown-properties: true   # matches api-spec.md "400 BAD_REQUEST on unknown field"

server:
  port: ${SERVER_PORT:8080}
  error:
    include-message: never         # never leak exception messages via Spring's default error body
    include-stacktrace: never

management:
  endpoints:
    web:
      exposure:
        include: ${ACTUATOR_EXPOSED_ENDPOINTS:health,info}
  endpoint:
    health:
      probes:
        enabled: true

app:
  auth:
    jwt:
      secret: ${JWT_SECRET}
      access-ttl-seconds: ${JWT_ACCESS_TTL_SECONDS:900}
      refresh-ttl-seconds: ${JWT_REFRESH_TTL_SECONDS:2592000}
    cookie:
      name: refresh_token
      # No `domain:` key — refresh cookie is host-only by design (see auth-flow.md).
      # Setting Domain would widen scope to subdomains, not narrow it.
      secure: ${REFRESH_COOKIE_SECURE:false}
      same-site: Lax
      path: /api/v1/auth
  attachment:
    presign-ttl-seconds: ${ATTACHMENT_PRESIGN_TTL_SECONDS:300}
    max-bytes: ${ATTACHMENT_MAX_BYTES:5242880}
    allowed-content-types: image/jpeg,image/png
  feature-flags:
    reminders: ${REMINDER_FEATURE_ENABLED:false}
    ai: ${AI_FEATURE_ENABLED:false}
```

---

## `application-dev.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/streakup?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: ${DB_USERNAME:streakup}
    password: ${DB_PASSWORD:streakup}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: ${REDIS_TIMEOUT_MS:2000}ms

logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.streakup: ${LOG_LEVEL_STREAKUP:DEBUG}
  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level [%X{traceId:-}] %logger{36} - %msg%n"

app:
  cors:
    allowed-origins: ${APP_FRONTEND_ORIGIN:http://localhost:5173}
  s3:
    region: ${AWS_REGION:ap-southeast-2}
    bucket: ${AWS_S3_BUCKET:streakup-dev-uploads}
    endpoint-override: ${AWS_S3_ENDPOINT_OVERRIDE:http://localhost:4566}   # LocalStack
```

---

## `application-test.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true                  # migrations run against the Testcontainers-provisioned MySQL
  datasource:
    # URL is injected by @Container / @DynamicPropertySource in AbstractIntegrationTest.
    # Hardcoded fallbacks here only matter if a test forgets the base class.
    url: jdbc:tc:mysql:8.0.36:///streakup_test?TC_REUSABLE=true
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  data:
    redis:
      # Host/port overridden by @DynamicPropertySource from the Redis Testcontainer.
      host: localhost
      port: 6379

logging:
  level:
    root: WARN
    com.streakup: INFO
    org.testcontainers: INFO
    com.github.dockerjava: WARN

app:
  auth:
    jwt:
      secret: test-secret-do-not-ship-32-bytes-min-length-padded
  feature-flags:
    reminders: false
    ai: false
  s3:
    region: us-east-1
    bucket: streakup-test
    endpoint-override: http://localhost:4566   # LocalStack container
```

### Testcontainers version pin

Only these tags are supported for the integration suite. **Do not let Testcontainers resolve "latest"** — DST behaviour and driver compatibility differ across patch versions.

| Container | Image tag | Why pinned |
|---|---|---|
| MySQL | `mysql:8.0.36` | Same version as RDS in prod; DST tables match. |
| Redis | `redis:7.2.4-alpine` | ElastiCache target engine version. |
| LocalStack | `localstack/localstack:3.3.0` | Pinned S3 presign-v4 behaviour. |

Define these constants once in `common.test.TestContainerImages` and reuse across every `@Container` field — no string literals scattered in test classes.

---

## `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD:}
      timeout: ${REDIS_TIMEOUT_MS:2000}ms
      ssl:
        # false for the MVP Docker sidecar; true once Redis moves to ElastiCache with in-transit encryption.
        enabled: ${REDIS_SSL_ENABLED:false}

logging:
  level:
    root: INFO
    com.streakup: INFO
  pattern:
    console: '{"ts":"%d{yyyy-MM-dd''T''HH:mm:ss.SSSXXX}","level":"%level","trace":"%X{traceId:-}","logger":"%logger","msg":"%message"}%n'

app:
  cors:
    allowed-origins: ${APP_FRONTEND_ORIGIN}
  s3:
    region: ${AWS_REGION}
    bucket: ${AWS_S3_BUCKET}
    # no endpoint-override in prod — real AWS S3
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    from: ${MAIL_FROM_ADDRESS}
```

Every value in `application-prod.yml` is an env-var read. SSM Parameter Store writes those env vars at container boot — there are no literal prod secrets in the repo.

---

## `@ValidTimezone` — custom validator

Referenced by:
- [ADR 0004](decisions/0004-timezone-strategy.md) — timezone strategy
- [api-spec.md](api-spec.md) — `POST /auth/register.timezone`, `PATCH /users/me.timezone`
- [error-codes.md](error-codes.md) — `TIMEZONE_INVALID` field code

### Annotation

```java
package com.streakup.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTimezoneValidator.class)
public @interface ValidTimezone {
    String message() default "{timezone.invalid}";      // mapped to `TIMEZONE_INVALID` in GlobalExceptionHandler
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### Validator

```java
package com.streakup.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DateTimeException;
import java.time.ZoneId;

public class ValidTimezoneValidator implements ConstraintValidator<ValidTimezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;                 // pair with @NotBlank if required
        try {
            ZoneId.of(value);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
```

### Usage

```java
public record UserUpdateRequest(
    @Size(min = 1, max = 50) String displayName,
    @ValidTimezone String timezone,
    Boolean leaderboardVisible
) {}
```

### Error mapping

`GlobalExceptionHandler` maps `ConstraintViolationException` → `ApiErrorResponse` with top-level `code: VALIDATION_FAILED`. The per-field `details[].code` for a failing `@ValidTimezone` check is the string `TIMEZONE_INVALID` (listed in [error-codes.md](error-codes.md)). The mapping comes from `messages.properties`:

```properties
timezone.invalid=TIMEZONE_INVALID
```

Convention: the `message()` default on each custom validator is a `{bundle.key}`, and the resolved value is the machine `code` — never a human sentence. Human copy goes into the frontend.

---

## Flyway conventions

- Location: `src/main/resources/db/migration/V{N}__{snake_case_description}.sql`. `N` is the numeric form used in [er-diagram.md](er-diagram.md) Migration Plan (`V1`, `V2`, ...; no zero-padding).
- Baseline: `baseline-on-migrate=false`. A fresh DB must apply `V1` first; we never skip history.
- Rollback: none shipped. Recovery path is a forward-fixing migration that undoes the broken state (e.g., `V7__drop_bad_index.sql`).
- `test` profile runs the full migration chain on every Testcontainers boot. Each integration test class can optionally use `@Sql("classpath:test-fixtures/x.sql")` for seed data, but never alters schema.
- If a migration must be hot-patched in prod: write the forward-fix, tag a new release, deploy. Never edit a committed `V*.sql`.

---

## Bean wiring notes (non-obvious pieces)

| Bean | Where | Why |
|---|---|---|
| `Clock` | `CommonTimeConfig` in `common.time` | All date/time logic uses this; tests override it with a `Clock.fixed(...)`. |
| `ObjectMapper` | Spring Boot default + a `JavaTimeModule` registration | `Instant` / `LocalDate` serialise as ISO-8601 per [api-spec.md](api-spec.md). |
| `RedisTemplate<String, String>` | `RedisConfig` | String-only template for streak counters and leaderboard ZSETs. Separate `RedisTemplate<String, Object>` with JSON serializer for heatmap cache. |
| `SpringDocConfig` | `common.openapi` | Groups endpoints by package, hides `/actuator/*` from the public spec. |
| `LockProvider` (ShedLock) | `SchedulerConfig` | JDBC provider; table defined in `V4__create_shedlock_table.sql`. Every `@Scheduled` job also has `@SchedulerLock`. |

---

## What this file is *not*

- Not the env var reference — that's [environment-variables.md](environment-variables.md).
- Not the schema definition — that's [er-diagram.md](er-diagram.md).
- Not coding standards — that's [../coding-standards.md](../coding-standards.md).
- Not security policy — that's [auth-flow.md](auth-flow.md) and the security section of [architecture.md](architecture.md).

If a question is "how does Spring actually pick up that env var / profile / validator?", it belongs here. If it's "what does the variable mean?", it belongs in the env var doc.
