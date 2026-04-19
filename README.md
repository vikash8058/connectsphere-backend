# ConnectSphere — Auth Service README

> **Service:** `auth-service`  
> **Port:** `8081`  
> **Base URL:** `http://localhost:8081/api/v1`  
> **Database:** `connectsphere_auth` (MySQL)  
> **Stack:** Java 17 · Spring Boot 3.2.0 · Spring Security · JWT · OAuth2 · JPA · AOP

---

## Table of Contents

1. [What This Service Does](#1-what-this-service-does)
2. [Package Structure — Every File Explained](#2-package-structure)
3. [Database — What Gets Created](#3-database)
4. [application.yml — Every Line Explained](#4-applicationyml)
5. [pom.xml — Every Dependency Explained](#5-pomxml)
6. [Entity Layer](#6-entity-layer)
7. [Repository Layer](#7-repository-layer)
8. [Security Layer](#8-security-layer)
9. [Service Layer](#9-service-layer)
10. [Controller Layer](#10-controller-layer)
11. [DTOs](#11-dtos)
12. [Exception Handling](#12-exception-handling)
13. [AOP — Logging Aspect](#13-aop)
14. [Email Service](#14-email-service)
15. [OTP Utility](#15-otp-utility)
16. [Unit Tests — What's Covered](#16-unit-tests)
17. [API Reference — All Endpoints](#17-api-reference)
18. [Complete Request Flows](#18-complete-request-flows)
19. [How This Service Connects to the Rest of the System](#19-how-this-connects-to-the-rest)
20. [Environment Variables](#20-environment-variables)
21. [Common Errors and Fixes](#21-common-errors-and-fixes)

---

## 1. What This Service Does

The auth-service is the **security and identity foundation** of ConnectSphere. Every other microservice depends on tokens issued by this service. No user can interact with posts, comments, likes, or follows without first going through auth-service.

**Responsibilities:**
- User registration with email + password (LOCAL provider)
- OTP-based email verification before first login
- Login → returns JWT access token + refresh token
- Google and GitHub OAuth2 social login
- JWT validation (called by API Gateway before forwarding protected requests)
- Token refresh (issue new access token using refresh token)
- True logout (token blacklisting — prevents reuse of logged-out tokens)
- Profile management (update username, bio, profilePicUrl, fullName)
- Password management (change password, forgot/reset via OTP)
- User search (by username or full name — used by @mention autocomplete)
- Admin: view all users, filter by role, suspend, reactivate, permanently delete, assign roles
- Moderator: view suspended accounts, fetch any user for review

**Roles supported:**

| Role | Who they are |
|---|---|
| `USER` | Default role assigned at registration. Can create posts, like, comment, follow. |
| `ADMIN` | Full platform access. Manages users, assigns roles, views analytics. |
| `MODERATOR` | Reviews flagged content and suspended accounts. Cannot delete users. |

---

## 2. Package Structure

```
auth-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/connectsphere/auth/
│   │   │   ├── AuthServiceApplication.java         ← Entry point
│   │   │   │
│   │   │   ├── aop/
│   │   │   │   └── LoggingAspect.java              ← Auto-logs all methods (AOP)
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── ApplicationConfig.java          ← @EnableAsync
│   │   │   │   └── SecurityConfig.java             ← Spring Security rules + CORS
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── AuthResource.java               ← All REST endpoints
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── ApiResponseDTO.java             ← Standard response wrapper
│   │   │   │   ├── AssignRoleRequestDTO.java       ← Admin: assign role payload
│   │   │   │   ├── ChangePasswordRequestDTO.java   ← Change password payload
│   │   │   │   ├── LoginRequestDTO.java            ← Login payload
│   │   │   │   ├── LoginResponseDTO.java           ← Login response (tokens + user)
│   │   │   │   ├── OtpVerifyRequestDTO.java        ← OTP verification payload
│   │   │   │   ├── RegisterRequestDTO.java         ← Registration payload
│   │   │   │   └── UpdateProfileRequestDTO.java    ← Profile update payload
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── AuthProvider.java               ← Enum: LOCAL / GITHUB / GOOGLE
│   │   │   │   ├── BlacklistedToken.java           ← Stores invalidated JWT tokens
│   │   │   │   ├── OtpType.java                   ← Enum: EMAIL_VERIFICATION / PASSWORD_RESET
│   │   │   │   ├── OtpVerification.java            ← OTP codes table
│   │   │   │   ├── Role.java                      ← Enum: USER / ADMIN / MODERATOR
│   │   │   │   └── User.java                      ← Main user entity
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java     ← Converts exceptions → ApiResponseDTO
│   │   │   │   ├── InvalidCredentialsException.java
│   │   │   │   ├── InvalidOtpException.java
│   │   │   │   ├── UnauthorizedAccessException.java
│   │   │   │   ├── UserAlreadyExistsException.java
│   │   │   │   └── UserNotFoundException.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── BlacklistedTokenRepository.java
│   │   │   │   ├── OtpVerificationRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java    ← Validates JWT per request
│   │   │   │   ├── JwtTokenProvider.java           ← Creates + validates JWT tokens
│   │   │   │   └── OAuth2SuccessHandler.java       ← Handles Google/GitHub login
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java               ← Interface (business contract)
│   │   │   │   ├── AuthServiceImpl.java            ← Implementation (all logic lives here)
│   │   │   │   └── EmailService.java              ← Async HTML email sender
│   │   │   │
│   │   │   └── util/
│   │   │       └── OtpUtil.java                   ← Secure 6-digit OTP generator
│   │   │
│   │   └── resources/
│   │       └── application.yml                    ← All configuration
│   │
│   └── test/
│       └── java/com/connectsphere/auth/
│           ├── AuthServiceApplicationTests.java
│           └── service/
│               └── AuthServiceImplTest.java       ← 40+ unit tests (Mockito)
```

---

## 3. Database

The service creates and manages its own database: `connectsphere_auth`.

Three tables are auto-created by Hibernate (`ddl-auto: update`):

### `users` table

| Column | Type | Notes |
|---|---|---|
| `user_id` | INT (PK, AI) | Auto-incremented primary key |
| `username` | VARCHAR(50) UNIQUE | @mention handle, must be unique |
| `full_name` | VARCHAR(100) | Display name on posts and profile |
| `email` | VARCHAR(150) UNIQUE | Login identifier, never updatable |
| `password_hash` | VARCHAR | BCrypt-hashed. NULL for OAuth users |
| `bio` | VARCHAR(300) | Optional profile bio |
| `profile_pic_url` | VARCHAR(500) | CDN URL of profile picture |
| `role` | VARCHAR(20) | Stored as string: `USER` / `ADMIN` / `MODERATOR` |
| `provider` | VARCHAR(20) | `LOCAL` / `GITHUB` / `GOOGLE` |
| `is_active` | BOOLEAN | `false` = account suspended |
| `is_email_verified` | BOOLEAN | LOCAL accounts must verify via OTP before login |
| `is_password_reset_verified` | BOOLEAN | Set `true` when PASSWORD_RESET OTP is verified. Reset to `false` after password is changed. Prevents skipping OTP step. |
| `created_at` | DATETIME | Auto-set on insert |
| `last_login_at` | DATETIME | Updated on every successful login |
| `updated_at` | DATETIME | Auto-updated by Hibernate |

### `otp_verifications` table

| Column | Type | Notes |
|---|---|---|
| `otp_id` | INT (PK, AI) | |
| `email` | VARCHAR(150) | Which user this OTP is for |
| `otp_code` | VARCHAR(10) | 6-digit code |
| `otp_type` | VARCHAR | `EMAIL_VERIFICATION` or `PASSWORD_RESET` |
| `expires_at` | DATETIME | 10 minutes after creation |
| `is_used` | BOOLEAN | Set to `true` after successful verification (prevents replay) |
| `created_at` | DATETIME | |

### `blacklisted_tokens` table

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AI) | |
| `token` | VARCHAR(512) UNIQUE | The full JWT string |
| `expiry_date` | DATETIME | When the token naturally expires (for cleanup) |

> Why blacklist? JWT is stateless — the server can't "delete" a token. By storing logged-out tokens in this table, the `JwtAuthenticationFilter` can check if a token has been revoked before allowing the request through.

---

## 4. application.yml

```yaml
spring:
  application:
    name: auth-service          # Registered under this name in Eureka
                                # Config Server serves 'auth-service.yml' to this service

  config:
    import: optional:configserver:http://localhost:8888
    # "optional:" means: if Config Server is down, start anyway using local config
    # If Config Server IS up, its auth-service.yml overrides local values

  datasource:
    url: jdbc:mysql://localhost:3306/connectsphere_auth?
         createDatabaseIfNotExist=true   # Creates DB automatically if missing
         &useSSL=false
         &serverTimezone=Asia/Kolkata    # Change to your timezone
    username: ${DB_USERNAME}             # Read from environment variable
    password: ${DB_PASSWORD}             # Read from environment variable
    driver-class-name: com.mysql.cj.jdbc.Driver

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}         # From Google Cloud Console
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: [email, profile]                # What we ask Google for
          github:
            client-id: ${GITHUB_CLIENT_ID}         # From GitHub OAuth App settings
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: [user:email]                    # What we ask GitHub for

    hikari:
      maximum-pool-size: 10       # Max simultaneous DB connections
      minimum-idle: 5             # Keep at least 5 connections ready
      connection-timeout: 20000   # Fail after 20s if no connection available

  jpa:
    hibernate:
      ddl-auto: update    # Auto-create/update tables. Use 'validate' in production.
    show-sql: true         # Print SQL to console (disable in production)
    properties:
      hibernate:
        format_sql: true   # Pretty-print SQL

  mail:
    host: ${MAIL_HOST}       # e.g. smtp.gmail.com
    port: ${MAIL_PORT}       # e.g. 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true   # TLS encryption

server:
  port: 8081
  servlet:
    context-path: /api/v1    # All endpoints get this prefix automatically
                              # So @PostMapping("/auth/login") → /api/v1/auth/login

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/   # Must match eureka-server port
    fetch-registry: true        # Download service list from Eureka
    register-with-eureka: true  # Register THIS service in Eureka
  instance:
    prefer-ip-address: true     # Use IP not hostname (important in containers)
    instance-id: auth-service:8081   # Unique ID shown in Eureka dashboard

jwt:
  secret: ${JWT_SECRET}                    # Must be identical in api-gateway
  expiration: ${JWT_EXPIRATION}            # e.g. 86400000 = 24 hours in ms
  refresh-expiration: ${JWT_REFRESH_EXPIRATION}  # e.g. 604800000 = 7 days

otp:
  expiry-minutes: 10   # OTP is valid for 10 minutes
  length: 6            # 6-digit code

management:
  endpoints.web.exposure.include: health,info,metrics
  endpoint.health.show-details: always    # Shows DB status in /actuator/health

logging:
  level:
    com.connectsphere.auth: DEBUG     # Full debug output for your code
    org.springframework.security: INFO
    org.hibernate.SQL: DEBUG          # See SQL in console
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

springdoc:
  api-docs.path: /api-docs            # JSON spec: http://localhost:8081/api/v1/api-docs
  swagger-ui.path: /swagger-ui.html   # UI: http://localhost:8081/api/v1/swagger-ui.html
```

---

## 5. pom.xml

Every dependency and why it's here:

| Dependency | Why it's needed |
|---|---|
| `spring-boot-starter-web` | Embeds Tomcat, enables `@RestController`, handles HTTP |
| `spring-boot-starter-security` | `SecurityConfig`, `@PreAuthorize`, BCrypt password encoding |
| `spring-boot-starter-data-jpa` | `@Entity`, `JpaRepository`, Hibernate ORM |
| `spring-boot-starter-validation` | `@NotBlank`, `@Email`, `@Pattern` on DTOs |
| `spring-boot-starter-mail` | `JavaMailSender` for sending HTML OTP emails |
| `spring-boot-starter-aop` | `@Aspect`, `@Around`, `@Before` for `LoggingAspect` |
| `spring-boot-starter-oauth2-client` | Google and GitHub OAuth2 login support |
| `spring-cloud-starter-netflix-eureka-client` | Registers with Eureka at startup |
| `spring-cloud-starter-config` | Fetches config from Config Server |
| `spring-boot-devtools` | Hot-reload in development (excluded from production jar) |
| `mysql-connector-j` | MySQL JDBC driver (runtime only) |
| `jjwt-api` | JWT creation/parsing API |
| `jjwt-impl` | JWT implementation (runtime) |
| `jjwt-jackson` | JWT JSON serialization (runtime) |
| `lombok` | `@Getter`, `@Setter`, `@Builder`, `@Slf4j` — reduces boilerplate |
| `spring-boot-starter-actuator` | `/actuator/health` endpoint |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI at `/swagger-ui.html` |
| `spring-boot-starter-test` | JUnit 5, Mockito, Spring test context |
| `spring-security-test` | `@WithMockUser` and security testing utilities |
| `spring-cloud-dependencies` (BOM) | Manages all Spring Cloud versions consistently |

---

## 6. Entity Layer

### `Role.java`
```
USER      → Default. Assigned to every new registration.
ADMIN     → Full platform access. Can assign roles, delete users, view analytics.
MODERATOR → Can review flagged content and suspended accounts.
```

### `AuthProvider.java`
```
LOCAL   → Registered with email + password. Requires OTP verification.
GITHUB  → Registered via GitHub OAuth2. Auto-verified, no password stored.
GOOGLE  → Registered via Google OAuth2. Auto-verified, no password stored.
```

### `OtpType.java`
```
EMAIL_VERIFICATION → Sent after LOCAL registration. Must be verified before first login.
PASSWORD_RESET     → Sent when user clicks "Forgot Password".
```

### `User.java` — Key design decisions

**`passwordHash`** is `null` for OAuth users (GITHUB/GOOGLE). Never store plain text passwords.

**`isEmailVerified`** gates login for LOCAL accounts. OAuth accounts are set to `true` automatically because the provider already verified the email.

**`isPasswordResetVerified`** is a flag on the User entity (not a separate table). Here's the full password reset flow it enables:
1. User calls `POST /auth/forgot-password` → OTP sent
2. User calls `POST /auth/verify-otp` with `otpType: PASSWORD_RESET` → sets `isPasswordResetVerified = true` on the user
3. User calls `POST /auth/reset-password` → service checks `isPasswordResetVerified == true`, resets password, then **clears the flag back to false**

This prevents someone from calling step 3 directly without going through the OTP step first.

**`isActive`** is the suspension flag. When `false`, the user cannot log in. Admin calls `/auth/admin/users/{userId}/deactivate` to set it `false`.

### `BlacklistedToken.java`
Stores JWT tokens that have been logged out. The `JwtAuthenticationFilter` checks this table before accepting any token. The `expiryDate` field allows a scheduled cleanup job to remove expired entries (optional — the check `existsByToken` will never find expired tokens anyway, but it keeps the table clean).

### `OtpVerification.java`
Each OTP record belongs to one email. The `isUsed` flag prevents OTP replay attacks. The `expiresAt` is checked by the repository query — expired OTPs are never returned. Records are deleted when a new OTP is resent to prevent accumulation.

---

## 7. Repository Layer

### `UserRepository.java`

```java
// Standard JPA derivation methods
findByEmail(String email)              → Login, profile fetch, OAuth lookup
findByUsername(String username)        → @mention resolution
existsByEmail(String email)            → Duplicate check during registration
existsByUsername(String username)      → Duplicate check during registration
findAllByRole(Role role)               → Admin: filter users by role
findByIsActive(Boolean isActive)       → Moderator: get suspended users (isActive=false)

// Custom JPQL queries
searchByUsername(String query)
  → LIKE search on both username AND fullName (case-insensitive)
  → Used by search endpoint and @mention autocomplete

updateLastLoginAt(Integer userId, LocalDateTime loginTime)
  → @Modifying — updates only the lastLoginAt column, no full entity load

deactivateByUserId(Integer userId)
  → @Modifying — sets isActive=false (suspension)

markEmailVerified(String email)
  → @Modifying — sets isEmailVerified=true after OTP verification

deleteByUserId(Integer userId)
  → @Modifying — hard delete (permanent removal by Admin)

updateRoleByUserId(Integer userId, Role role)
  → @Modifying — Admin-only role assignment
```

### `OtpVerificationRepository.java`

```java
findValidOtp(String email, OtpType otpType)
  → JPQL query with 3 conditions:
    1. isUsed = false       (not already consumed)
    2. expiresAt > NOW()    (not expired)
    3. ordered by createdAt DESC (latest OTP wins if multiple exist)

markAsUsed(Integer otpId)
  → @Modifying — sets isUsed=true to prevent replay

deleteAllByEmailAndOtpType(String email, OtpType type)
  → Cleans up old OTPs before issuing a new one (resendOtp, forgotPassword)
```

### `BlacklistedTokenRepository.java`

```java
existsByToken(String token)
  → Called by JwtAuthenticationFilter on EVERY request
  → Returns true if the token was logged out
```

---

## 8. Security Layer

### `JwtTokenProvider.java`

Creates and validates JWT tokens. The same `jwt.secret` is also used in the API Gateway's `JwtAuthenticationFilter` — they must be identical or every protected request will return 401.

**Token claims (payload inside each JWT):**

```json
{
  "sub": "vikash@test.com",    ← email — Spring Security principal
  "userId": 1,                 ← integer ID
  "username": "vikash",        ← for @mentions
  "role": "USER",              ← for @PreAuthorize checks
  "fullName": "Vikash Prajapati",
  "tokenType": "ACCESS",
  "iat": 1713344000,           ← issued at
  "exp": 1713430400            ← expires at (24h later)
}
```

**Refresh token payload** (simpler, no role/username):
```json
{
  "sub": "vikash@test.com",
  "userId": 1,
  "tokenType": "REFRESH",
  "iat": ...,
  "exp": ...  ← 7 days later
}
```

**`extractExpiration(String token)`** — called during logout to store the token's natural expiry date in the `blacklisted_tokens` table. This allows cleanup of expired entries.

### `JwtAuthenticationFilter.java`

Runs on **every request** (extends `OncePerRequestFilter`).

```
Incoming request
    ↓
Extract "Bearer <token>" from Authorization header
    ↓
jwtTokenProvider.validateToken(token)       ← Checks signature + expiry
    ↓
blacklistedTokenRepository.existsByToken()  ← Checks if logged out
    ↓
If both pass: set Authentication in SecurityContext
    ↓
@PreAuthorize and SecurityConfig rules now work correctly
```

If the filter doesn't set authentication, Spring Security treats the request as unauthenticated (which is correct for public endpoints — they don't send a token).

### `OAuth2SuccessHandler.java`

Called by Spring Security after a successful Google or GitHub OAuth2 login. This is where your application code takes over from the OAuth2 flow.

**Full flow:**
1. User clicks "Login with Google" in the frontend
2. Spring Security handles the OAuth2 redirect/callback
3. `OAuth2SuccessHandler.onAuthenticationSuccess()` is called with the user's Google/GitHub profile
4. Handler extracts email and name from the OAuth2User attributes
5. Checks if a user with that email already exists:
   - **Yes** → update `lastLoginAt`, issue JWT
   - **No** → create new `User` (role=USER, isEmailVerified=true, no password), issue JWT
6. Returns JWT tokens in the JSON response body

**GitHub email note:** If a GitHub user has set their email to private, `oAuth2User.getAttribute("email")` returns `null`. The handler falls back to `{githubLogin}@github-noreply.com` as a placeholder and logs a warning.

**Username generation for OAuth users:** Since they don't choose a username during OAuth flow, one is auto-generated from their email prefix + a random 5-char suffix: `vikash_a3f9b`. The uniqueness loop handles the rare collision case.

### `SecurityConfig.java`

Two layers of protection for every sensitive endpoint:

**Layer 1 — URL-level** (SecurityFilterChain):
```java
.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
.requestMatchers("/auth/admin/**").hasRole("ADMIN")
.requestMatchers("/auth/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
.anyRequest().authenticated()
```

**Layer 2 — Method-level** (on each controller method):
```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
```

This is "defense in depth." Even if a misconfigured proxy bypasses the URL filter, `@PreAuthorize` still enforces role checks. Both layers must be satisfied.

**Public endpoints** (no token needed):
```
/auth/register, /auth/login, /auth/verify-otp, /auth/resend-otp,
/auth/forgot-password, /auth/reset-password, /auth/refresh,
/oauth2/**, /login/**,
/actuator/health, /swagger-ui/**, /api-docs/**
```

**`@EnableAsync`** in `ApplicationConfig.java` enables `@Async` on `EmailService.sendOtpEmail()`. This makes email sending non-blocking — the API response returns immediately after saving the OTP, and the email goes out in a background thread.

---

## 9. Service Layer

### `AuthService.java` (Interface)

Defines the full contract. Every method the controller calls is declared here. `AuthServiceImpl` provides the implementation.

### `AuthServiceImpl.java` — All Business Logic

#### Registration Flow
```
1. existsByEmail()          → throw UserAlreadyExistsException if taken
2. existsByUsername()       → throw UserAlreadyExistsException if taken
3. passwordEncoder.encode() → bcrypt hash the password (strength=12)
4. userRepository.save()    → persist: role=USER, provider=LOCAL, isEmailVerified=false
5. sendOtpToEmail()         → generate 6-digit OTP, save to DB, send email async
```

#### OTP Verification Flow
```
For EMAIL_VERIFICATION:
  1. findValidOtp() → must be unused and not expired
  2. Compare otpCode → throw InvalidOtpException if wrong
  3. markAsUsed()    → prevent replay
  4. markEmailVerified() → user can now login

For PASSWORD_RESET:
  1. findValidOtp() → must be unused and not expired
  2. Compare otpCode → throw InvalidOtpException if wrong
  3. markAsUsed()
  4. Set user.isPasswordResetVerified = true → allows resetPassword() to proceed
```

#### Login Flow
```
1. findByEmail()            → throw InvalidCredentialsException if not found
2. passwordEncoder.matches()→ throw InvalidCredentialsException if wrong
3. isActive check           → throw InvalidCredentialsException if suspended
4. isEmailVerified check    → throw InvalidCredentialsException if not verified
5. generateAccessToken()    → 24h JWT with userId, username, role, fullName claims
6. generateRefreshToken()   → 7d JWT with userId only
7. updateLastLoginAt()      → record login time
8. return LoginResponseDTO  → tokens + user profile (userId, username, bio, etc.)
```

#### Logout Flow (Token Blacklisting)
```
1. existsByToken()     → if already blacklisted, return success silently
2. extractExpiration() → get token's natural expiry date
3. save BlacklistedToken(token, expiryDate) to DB
4. JwtAuthenticationFilter will now reject this token on future requests
```

#### Password Reset Flow
```
Step 1: forgotPassword(email)
  - If email not registered: return success anyway (security — don't reveal)
  - If registered: delete old PASSWORD_RESET OTPs, send new one

Step 2: verifyOtp(email, code, PASSWORD_RESET)
  - Verify OTP code
  - Set user.isPasswordResetVerified = true
  - NOTE: does NOT check isUsed here for PASSWORD_RESET type
    (by design — the isPasswordResetVerified flag is the gate instead)

Step 3: resetPassword(email, newPassword)
  - Check user.isPasswordResetVerified == true (else throw InvalidOtpException)
  - bcrypt encode newPassword
  - Set isPasswordResetVerified = false (clear flag)
  - Save user
```

#### Admin: deleteUser vs deactivateUser
- **`deactivateUser(userId)`** → Soft suspend. Sets `isActive=false`. User cannot log in. Reversible via `reactivateUser()`.
- **`deleteUser(adminId, targetUserId)`** → Hard delete. Removes the row from `users` table permanently. Admin cannot delete their own account (self-protection check: `adminId.equals(targetUserId)` → `UnauthorizedAccessException`).

#### Admin: assignRole
Promotes or demotes a user. Admin cannot change their own role. Role string is validated against `Role.valueOf()` — invalid strings throw `IllegalArgumentException`.

#### Moderator: getSuspendedUsers
Returns `findByIsActive(false)` — all accounts currently suspended. Used by moderators during content review to check if the reporter or reported user is already suspended.

---

## 10. Controller Layer

### `AuthResource.java`

Base mapping: `@RequestMapping("/auth")` → full path is `/api/v1/auth`

**How the controller gets the current user's identity:**

For protected endpoints, instead of accepting `userId` as a path variable (which would let anyone forge requests), the controller reads it from the Spring Security context — which was populated by `JwtAuthenticationFilter` from the JWT:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String email = auth.getName();  // the 'sub' claim from the JWT
```

This means a user can only update their own profile — they can't pass someone else's email.

**Two layers of protection on admin endpoints:**
```java
// SecurityConfig: URL-level
.requestMatchers("/auth/admin/**").hasRole("ADMIN")

// Controller: method-level  
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<...> deleteUser(...) { ... }
```

The `deleteUser` and `assignRole` methods extract the admin's ID from the security context to pass to the service for the self-protection check.

---

## 11. DTOs

### `ApiResponseDTO<T>` — Standard Response Wrapper

Every endpoint returns this wrapper. Three static factory methods:

```java
ApiResponseDTO.success("Login successful", loginResponseDTO)
// → { "success": true, "message": "Login successful", "data": {...}, "timestamp": "..." }

ApiResponseDTO.success("Registration successful!")
// → { "success": true, "message": "...", "timestamp": "..." }

ApiResponseDTO.error("Invalid email or password")
// → { "success": false, "message": "...", "timestamp": "..." }
```

`@JsonInclude(NON_NULL)` — the `data` field is omitted from JSON if null (cleaner error responses).

### `RegisterRequestDTO` — Validation Rules

| Field | Rule |
|---|---|
| `username` | 3–50 chars, only `[a-zA-Z0-9._]` |
| `fullName` | 2–100 chars |
| `email` | Valid email format |
| `password` | Min 8 chars, must contain: digit, lowercase, uppercase, special char `[@#$%^&+=]` |

### `LoginResponseDTO` — Returned After Login

Contains everything the frontend needs to initialize the session:
```
accessToken, refreshToken, tokenType("Bearer"), expiresIn(ms),
userId, username, fullName, email, bio, profilePicUrl, role, provider
```

### `OtpVerifyRequestDTO`
- `email` — whose OTP
- `otpCode` — the 6-digit code the user entered
- `otpType` — `EMAIL_VERIFICATION` or `PASSWORD_RESET` (enum value)

### `UpdateProfileRequestDTO`
All fields optional (null = don't update). Email is intentionally NOT here — email is the login identifier and is never changed.

### `AssignRoleRequestDTO`
```json
{ "role": "MODERATOR" }
```
Only field. Admin calls `PUT /auth/admin/users/{userId}/role` with this body.

---

## 12. Exception Handling

`GlobalExceptionHandler` converts every exception into the standard `ApiResponseDTO` format. No raw stack traces ever reach the client.

| Exception | HTTP Status | When thrown |
|---|---|---|
| `UserAlreadyExistsException` | 409 Conflict | Email or username already registered |
| `UserNotFoundException` | 404 Not Found | User ID or email doesn't exist |
| `InvalidCredentialsException` | 401 Unauthorized | Wrong password, suspended account, unverified email |
| `InvalidOtpException` | 400 Bad Request | Wrong OTP code, expired OTP, reset not verified |
| `UnauthorizedAccessException` | 403 Forbidden | Admin tries to delete/change their own account |
| `AccessDeniedException` | 403 Forbidden | Spring Security: `@PreAuthorize` failed |
| `IllegalArgumentException` | 400 Bad Request | Invalid role string, password mismatch |
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` DTO validation failed |
| `Exception` (catchall) | 500 Internal Server Error | Anything unexpected |

The `AccessDeniedException` handler is important — without it, Spring Security returns a plain 403 HTML page instead of our `ApiResponseDTO`.

---

## 13. AOP

### `LoggingAspect.java`

Zero configuration needed — automatically applied to all methods in controller, service, and repository packages.

**Four advice types:**

| Advice | When it runs | What it logs |
|---|---|---|
| `@Before` (applicationLayer) | Before every method | Method name + arguments |
| `@AfterReturning` (applicationLayer) | After method returns | Method name + return type |
| `@AfterThrowing` (applicationLayer) | After exception | Method name + exception class + message |
| `@Around` (serviceLayer only) | Wraps service methods | Execution time in ms |

**Slow method warning:** If any service method takes more than 1500ms, a `WARN` log is emitted. This aligns with the ConnectSphere NFR requirement of 1.5s response time.

**Console output example:**
```
>>> ENTERING [AuthServiceImpl.login] args: [LoginRequestDTO(...)]
⏱ [AuthServiceImpl.login] completed in 243 ms
<<< EXITING [AuthServiceImpl.login] returned: ApiResponseDTO
```

---

## 14. Email Service

### `EmailService.java`

`@Async` — email sending runs in a background thread. The API response returns immediately; the email goes out asynchronously. `@EnableAsync` in `ApplicationConfig.java` makes this work.

**Sends two types of HTML email:**

**Email Verification** (`EMAIL_VERIFICATION`)
- Subject: "ConnectSphere – Verify Your Email"
- Blue OTP code in large text
- 10-minute expiry notice

**Password Reset** (`PASSWORD_RESET`)
- Subject: "ConnectSphere – Password Reset OTP"
- Red OTP code in large text
- 10-minute expiry notice

Both emails are HTML-only (no plain text fallback). If email sending fails (e.g. wrong SMTP credentials), the error is logged but the API response still succeeds — the OTP is already saved in the DB and the user can resend.

---

## 15. OTP Utility

### `OtpUtil.java`

```java
SecureRandom secureRandom = new SecureRandom();
int otp = 100000 + secureRandom.nextInt(900000);
// Always produces a 6-digit number: 100000 to 999999
```

Uses `java.security.SecureRandom` (cryptographically strong), not `Math.random()`. This ensures OTPs are not predictable even if the attacker knows the seed.

---

## 16. Unit Tests

### `AuthServiceImplTest.java`

**Test framework:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)

All dependencies are mocked — no Spring context, no DB, no email server. Tests run in milliseconds.

**Test data setup (`@BeforeEach`):**
- `regularUser` — role=USER, isActive=true, isEmailVerified=true
- `adminUser` — role=ADMIN, isActive=true
- `moderatorUser` — role=MODERATOR, isActive=true
- `suspendedUser` — role=USER, isActive=**false**

**Test groups (9 `@Nested` classes):**

| Nested Class | Tests | What's verified |
|---|---|---|
| `RegisterTests` | 4 | Success path, duplicate email, duplicate username, default role=USER/provider=LOCAL |
| `LoginTests` | 6 | USER/ADMIN/MODERATOR success, wrong password, user not found, suspended, email not verified |
| `OtpTests` | 3 | Correct OTP verifies email, wrong code fails, expired OTP fails |
| `PasswordTests` | 7 | Change password success/fail, forgotPassword sends OTP, forgotPassword silent for unknown email, resetPassword needs verified flag, resetPassword clears flag |
| `ProfileTests` | 4 | getUserById found/not found, updateProfile success, username conflict |
| `TokenTests` | 5 | Logout blacklists token, logout idempotent, validateToken pass, blacklisted token fails, refreshToken issues new token |
| `AdminTests` | 14 | getAllUsers, getUsersByRole for all roles, deactivate/reactivate, deleteUser (success/self-delete/not-found), assignRole (all role combinations/self-change/invalid role) |
| `ModeratorTests` | 3 | getSuspendedUsers returns inactive accounts, empty list, getUserById for review |
| `SearchTests` | 2 | matches returned, empty list on no match |

**Total: ~40+ test cases covering all happy paths and all error paths.**

---

## 17. API Reference

All URLs prefixed with `/api/v1`

### Public Endpoints (no JWT)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/auth/register` | `RegisterRequestDTO` | Register. Sends OTP email. |
| POST | `/auth/verify-otp` | `OtpVerifyRequestDTO` | Verify email or password reset OTP |
| POST | `/auth/resend-otp` | `?email=&otpType=` | Resend OTP (params in query string) |
| POST | `/auth/login` | `LoginRequestDTO` | Login → JWT tokens |
| POST | `/auth/refresh` | `{"refreshToken": "..."}` | Get new access token |
| POST | `/auth/forgot-password` | `{"email": "..."}` | Send PASSWORD_RESET OTP |
| POST | `/auth/reset-password` | `{"email": "...", "newPassword": "..."}` | Reset password (OTP must be verified first) |

### Protected Endpoints (Bearer token required)

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/auth/logout` | — | Blacklist current token |
| GET | `/auth/validate` | — | Validate JWT (used by API Gateway) |
| GET | `/auth/profile` | — | Get own profile (from JWT subject) |
| PUT | `/auth/profile` | `UpdateProfileRequestDTO` | Update own profile |
| PUT | `/auth/password` | `ChangePasswordRequestDTO` | Change own password |
| GET | `/auth/search?query=` | — | Search users by username/fullName |
| GET | `/auth/users/{userId}` | — | Get any user's profile by ID |

### Admin Only (`hasRole('ADMIN')`)

| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/auth/admin/users` | — | All users |
| GET | `/auth/admin/users/role/{role}` | — | Users by role (USER/ADMIN/MODERATOR) |
| GET | `/auth/admin/users/{userId}` | — | Full user details by ID |
| PUT | `/auth/admin/users/{userId}/deactivate` | — | Suspend (soft-disable) |
| PUT | `/auth/admin/users/{userId}/reactivate` | — | Restore suspended user |
| DELETE | `/auth/admin/users/{userId}` | — | Permanently delete user |
| PUT | `/auth/admin/users/{userId}/role` | `AssignRoleRequestDTO` | Assign/change role |

### Moderator + Admin (`hasAnyRole('ADMIN','MODERATOR')`)

| Method | Path | Description |
|---|---|---|
| GET | `/auth/moderator/users/suspended` | All suspended accounts |
| GET | `/auth/moderator/users/{userId}` | Any user by ID for review |

### OAuth2 (handled by Spring Security automatically)

| Path | Description |
|---|---|
| `/oauth2/authorization/google` | Redirect user here to start Google login |
| `/oauth2/authorization/github` | Redirect user here to start GitHub login |
| `OAuth2SuccessHandler` fires after callback | Issues JWT and returns JSON |

---

## 18. Complete Request Flows

### Flow 1: Registration + Email Verification + Login

```
1. POST /auth/register
   Body: { username, fullName, email, password }

   Auth Service:
   ├── Check email not taken
   ├── Check username not taken
   ├── BCrypt hash password
   ├── Save User (isEmailVerified=false, role=USER)
   ├── Generate 6-digit OTP
   ├── Save OtpVerification record
   └── Send HTML email ASYNC → return 201

2. POST /auth/verify-otp
   Body: { email, otpCode: "123456", otpType: "EMAIL_VERIFICATION" }

   Auth Service:
   ├── findValidOtp(email, EMAIL_VERIFICATION) → must be unused + not expired
   ├── Compare otpCode → throw InvalidOtpException if mismatch
   ├── markAsUsed(otpId)
   ├── markEmailVerified(email)
   └── return 200

3. POST /auth/login
   Body: { email, password }

   Auth Service:
   ├── findByEmail(email)
   ├── passwordEncoder.matches()
   ├── Check isActive=true
   ├── Check isEmailVerified=true
   ├── generateAccessToken() → 24h JWT
   ├── generateRefreshToken() → 7d JWT
   ├── updateLastLoginAt()
   └── return LoginResponseDTO (both tokens + user profile)
```

### Flow 2: Password Reset

```
1. POST /auth/forgot-password
   Body: { email: "vikash@test.com" }
   → Sends PASSWORD_RESET OTP to email

2. POST /auth/verify-otp
   Body: { email, otpCode, otpType: "PASSWORD_RESET" }
   → Sets user.isPasswordResetVerified = true

3. POST /auth/reset-password
   Body: { email, newPassword }
   → Checks isPasswordResetVerified == true
   → BCrypt encodes newPassword
   → Clears isPasswordResetVerified = false
   → User can now login with new password
```

### Flow 3: Logout + Rejected Reuse

```
1. POST /auth/logout
   Header: Authorization: Bearer <token>
   → Token saved to blacklisted_tokens table

2. GET /auth/profile
   Header: Authorization: Bearer <same token>
   → JwtAuthenticationFilter:
     ├── jwtTokenProvider.validateToken() → true (still valid signature)
     └── blacklistedTokenRepository.existsByToken() → true (blacklisted)
   → Authentication NOT set in SecurityContext
   → Spring Security returns 401 Unauthorized
```

### Flow 4: Token Refresh

```
POST /auth/refresh
Body: { "refreshToken": "<7d token>" }

Auth Service:
├── validateToken(refreshToken)     → signature + expiry check
├── getEmailFromToken()             → extract subject
├── findByEmail()                   → load user
├── generateAccessToken()           → new 24h token
└── return LoginResponseDTO (new access + same refresh token)
```

### Flow 5: OAuth2 Google Login

```
1. Frontend redirects user to:
   http://localhost:8080/api/v1/oauth2/authorization/google

2. Spring Security handles redirect to Google → user consents → callback

3. OAuth2SuccessHandler.onAuthenticationSuccess() fires:
   ├── Extract email + name from Google attributes
   ├── findByEmail(email)?
   │   ├── YES → update lastLoginAt
   │   └── NO  → create User (role=USER, provider=GOOGLE, isEmailVerified=true)
   ├── generateAccessToken() + generateRefreshToken()
   └── Write JSON response with tokens + user profile
```

---

## 19. How This Connects to the Rest of the System

**API Gateway → Auth Service:**
- `POST /api/v1/auth/login` is a public route (no JWT check by Gateway)
- `GET /api/v1/auth/validate` is called by Gateway's `JwtAuthenticationFilter` to check blacklisted tokens (optional — Gateway currently validates the signature itself, but can delegate)
- `GET /api/v1/auth/validate` returns `true/false` (raw boolean, not wrapped in `ApiResponseDTO`)

**Other microservices → Auth Service:**
- `post-service`, `comment-service` etc. receive `X-User-Id` and `X-User-Role` headers from the API Gateway (populated from JWT claims after Gateway validates the token)
- They do NOT call auth-service directly for every request — the Gateway does the token validation upfront
- If a service needs a user's full profile (e.g. to display author name on a post), it can call `GET /api/v1/auth/users/{userId}` via RestTemplate

**JWT Secret sharing:**
- `auth-service`: creates tokens using `JWT_SECRET`
- `api-gateway`: validates tokens using the **same** `JWT_SECRET`
- Other services: optionally validate tokens using the **same** `JWT_SECRET` (they have their own `JwtAuthenticationFilter`)
- **All must use the identical `JWT_SECRET` environment variable value**

---

## 20. Environment Variables

Set these before starting auth-service:

```bash
# Database
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# JWT — must be identical in api-gateway
JWT_SECRET=connectsphere-super-secret-key-must-be-at-least-256-bits-long!!
JWT_EXPIRATION=86400000            # 24 hours in milliseconds
JWT_REFRESH_EXPIRATION=604800000   # 7 days in milliseconds

# Mail (Gmail example)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password   # Use App Password, not account password

# OAuth2 — from Google Cloud Console
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# OAuth2 — from GitHub Settings → Developer Settings → OAuth Apps
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
```

**Gmail App Password setup:**
1. Enable 2FA on your Gmail account
2. Go to Google Account → Security → App Passwords
3. Generate password for "Mail" → use that as `MAIL_PASSWORD`
4. Do NOT use your regular Gmail password

**JWT_SECRET length:** Must be at least 32 characters (256 bits) for HMAC-SHA256. Use a long random string.

---

## 21. Common Errors and Fixes

### `No value present` / `Optional.empty()` on startup

**Cause:** Config Server is up but has an `auth-service.yml` with missing or wrong DB credentials.  
**Fix:** Check `config-server/src/main/resources/config/auth-service.yml` — verify `DB_USERNAME` and `DB_PASSWORD` resolve correctly.

---

### `Communications link failure` — MySQL connection error

**Cause:** MySQL not running, or wrong port/database name.  
**Fix:** Start MySQL. Verify `connectsphere_auth` DB exists (or let `createDatabaseIfNotExist=true` create it). Check `DB_USERNAME` / `DB_PASSWORD`.

---

### `401 Unauthorized` on every request even with correct token

**Cause 1:** `JWT_SECRET` in auth-service and api-gateway don't match.  
**Fix:** Make sure both use the exact same `JWT_SECRET` env variable value.

**Cause 2:** Token was blacklisted (logged out). The user needs to login again.

---

### `403 Forbidden` on admin endpoints

**Cause:** The JWT's `role` claim is `USER` but the endpoint requires `ADMIN`.  
**Fix:** Login with an ADMIN account. Only accounts with `role=ADMIN` in the database can access `/auth/admin/**`.

---

### `OTP is invalid or has expired`

**Cause 1:** User waited more than 10 minutes.  
**Fix:** Call `POST /auth/resend-otp` to get a fresh OTP.

**Cause 2:** Wrong `otpType` in the request.  
**Fix:** Verify the `otpType` matches what was sent — `EMAIL_VERIFICATION` or `PASSWORD_RESET`.

---

### `OTP verification required before resetting password`

**Cause:** User called `POST /auth/reset-password` without first calling `POST /auth/verify-otp` with `otpType: PASSWORD_RESET`.  
**Fix:** Complete the full 3-step reset flow: `forgot-password` → `verify-otp` → `reset-password`.

---

### `Username 'x' is already taken` during profile update

**Cause:** Another user already has that username.  
**Fix:** Choose a different username. The update skips the uniqueness check if the new username is the same as the current one (so users can "save" their profile without changing username).

---

### `Admin cannot permanently delete their own account`

**Cause:** Admin is trying to delete their own userId.  
**Fix:** This is a hard business rule. To remove an admin account, another admin must do it, or deactivate it using `/auth/admin/users/{userId}/deactivate` instead.

---

### OAuth2 login fails with `Unknown OAuth2 provider`

**Cause:** `spring.security.oauth2.client.registration` in `application.yml` has a provider registered under an unexpected name.  
**Fix:** The registration IDs must be exactly `google` and `github` (lowercase) — Spring Security uses these to match the registrationId in `OAuth2AuthenticationToken`.

---

*ConnectSphere Auth Service README — Version 1.0 — 2026*