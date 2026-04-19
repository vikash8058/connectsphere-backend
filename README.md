# ConnectSphere — Infrastructure Services README

> **Covers:** Eureka Server · Config Server · API Gateway  
> **Stack:** Java 17 · Spring Boot 3.2.0 · Spring Cloud 2023.0.0  

---

## Table of Contents

1. [Overview — What These Three Services Do](#1-overview)
2. [Startup Order — Read This First](#2-startup-order)
3. [Environment Variables — Required Before Running](#3-environment-variables)
4. [Eureka Server](#4-eureka-server)
5. [Config Server](#5-config-server)
6. [API Gateway](#6-api-gateway)
7. [How All Three Work Together — Request Flow](#7-how-all-three-work-together)
8. [Port Map — All Services](#8-port-map)
9. [Common Errors and Fixes](#9-common-errors-and-fixes)

---

## 1. Overview

These three services are the **infrastructure backbone** of ConnectSphere. Your actual business services (auth, post, comment, like, follow, notification, media, search) depend on all three of these being up and healthy before they start.

| Service | What it does | Port |
|---|---|---|
| **Eureka Server** | Service registry — all microservices register here so they can find each other by name instead of hardcoded IP | `8761` |
| **Config Server** | Centralized config store — serves `application.yml` properties to each microservice so you don't repeat DB credentials, JWT secrets etc. in every service | `8888` |
| **API Gateway** | Single entry point for all client requests — validates JWT, routes to the correct microservice, adds CORS headers | `8080` |

No business logic lives here. These three are pure infrastructure.

---

## 2. Startup Order

**This order is mandatory.** If you start them out of order, services will fail to register or fetch config.

```
1. Eureka Server   (port 8761)   ← Start first, always
2. Config Server   (port 8888)   ← Needs Eureka to register itself
3. Business Services             ← auth (8081), post (8082), comment (8083), etc.
4. API Gateway     (port 8080)   ← Start last, needs all services already in Eureka
```

**Why this order?**  
Config Server registers with Eureka on startup. Business services fetch config from Config Server at startup and also register with Eureka. API Gateway discovers all services from Eureka to set up routing — so Eureka must already have everyone registered when Gateway starts.

---

## 3. Environment Variables

Set these in your OS, `.env` file, or IDE run configuration **before starting anything**.  
All three infrastructure services + all business services read from the same set of variables.

```bash
# Database
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# JWT — MUST be identical across all services that validate tokens
# (auth-service, post-service, comment-service, api-gateway all use this)
JWT_SECRET=connectsphere-super-secret-key-must-be-at-least-256-bits-long!!
JWT_EXPIRATION=86400000          # 24 hours in milliseconds
JWT_REFRESH_EXPIRATION=604800000 # 7 days in milliseconds

# Mail (only needed for auth-service and notification-service)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password

# OAuth2 (only needed for auth-service)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
```

> **JWT_SECRET tip:** Must be the same string in all services. The API Gateway validates tokens using this secret. The auth-service creates tokens using this secret. If they differ, every protected API call will return 401.

---

## 4. Eureka Server

### What It Is

Eureka is Netflix's service registry, integrated into Spring Cloud. When any microservice starts, it calls Eureka and says "I'm alive, I'm `auth-service`, I'm at IP `192.168.1.5:8081`." When another service (or the API Gateway) needs to call `auth-service`, it asks Eureka for the current address instead of using a hardcoded URL. This is called **service discovery**.

### Package Structure

```
eureka-server/
└── src/main/java/com/connectsphere/eureka/
│   └── EurekaServerApplication.java      ← Only file, no business logic
└── src/main/resources/
    └── application.yml
```

### `EurekaServerApplication.java` — Explained

```java
@SpringBootApplication
@EnableEurekaServer   // ← This single annotation turns the app into a registry
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

`@EnableEurekaServer` is the only thing that makes this different from a regular Spring Boot app. There is no controller, no service, no repository — Eureka handles everything internally.

### `application.yml` — Explained Line by Line

```yaml
spring:
  application:
    name: eureka-server         # This name appears in logs

server:
  port: 8761                    # Standard Eureka port — do not change

eureka:
  instance:
    hostname: localhost          # The hostname this server advertises

  client:
    register-with-eureka: false # IMPORTANT: The server does NOT register with itself
    fetch-registry: false       # The server does NOT fetch a registry from itself
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

  server:
    eviction-interval-timer-in-ms: 5000   # Check every 5s for dead instances
    enable-self-preservation: false        # Dev mode — evict dead services immediately
                                          # Set to true in production
    renewal-percent-threshold: 0.49       # Related to self-preservation threshold

management:
  endpoints:
    web:
      exposure:
        include: health,info    # Expose /actuator/health endpoint

logging:
  level:
    com.netflix.eureka: INFO
    com.netflix.discovery: INFO
```

**The two most important lines:**
- `register-with-eureka: false` — The Eureka Server must NOT register with itself, or you get a circular reference error on startup.
- `fetch-registry: false` — Same reason. The server IS the registry. It doesn't fetch from itself.

Every other microservice has these set to `true` because they are clients.

### `pom.xml` — Key Dependency

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

That's the only Spring Cloud dependency. No Eureka Client — the server does not need to register itself.

### How to Verify It's Running

Open your browser: **http://localhost:8761**

You'll see the Eureka dashboard. As your other services start, their names appear under **"Instances currently registered with Eureka"**. If a service fails to register, it won't appear here — that's your first debugging signal.

---

## 5. Config Server

### What It Is

The Config Server is a centralized place to store configuration. Instead of each microservice maintaining its own DB credentials, JWT secrets, and other properties in its own `application.yml`, those values live in the Config Server. Each microservice fetches its config at startup with one line:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

The word `optional:` means the service will still start even if Config Server is down — it'll just use its own local `application.yml` instead.

### Package Structure

```
config-server/
└── src/main/java/com/connectsphere/config/
│   └── ConfigServerApplication.java         ← Only file, no business logic
└── src/main/resources/
    ├── application.yml                        ← Config Server's own config
    └── config/                                ← Config files served TO other services
        ├── auth-service.yml
        ├── post-service.yml
        ├── comment-service.yml
        ├── like-service.yml
        ├── follow-service.yml
        ├── notification-service.yml
        ├── media-service.yml
        └── search-service.yml
```

### `ConfigServerApplication.java` — Explained

```java
@SpringBootApplication
@EnableConfigServer      // ← Turns this app into a Config Server
@EnableDiscoveryClient   // ← Registers with Eureka so services can find it by name
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

Two annotations do all the work:
- `@EnableConfigServer` — Makes this app serve config files to other services.
- `@EnableDiscoveryClient` — Registers with Eureka (which is why Eureka must be up first).

### `application.yml` — Explained Line by Line

```yaml
spring:
  application:
    name: config-server

  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configs/
          # "native" means: read config files from the local filesystem / classpath.
          # The config files (auth-service.yml, post-service.yml etc.) are stored
          # at src/main/resources/configs/ inside this project.
          #
          # Alternative for production — read from a Git repository:
          # git:
          #   uri: https://github.com/your-org/connectsphere-configs
          #   clone-on-start: true

  profiles:
    active: native    # Activates the "native" (filesystem) config backend

server:
  port: 8888          # Standard Config Server port

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    fetch-registry: true
    register-with-eureka: true   # Config Server IS a client — it registers with Eureka
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
```

### How Config Files Work — The Naming Convention

When `auth-service` starts and fetches config, the Config Server looks for a file named exactly `auth-service.yml` in `src/main/resources/configs/`. The service name in `spring.application.name` must match the filename.

| Service's `spring.application.name` | Config file served |
|---|---|
| `auth-service` | `configs/auth-service.yml` |
| `post-service` | `configs/post-service.yml` |
| `comment-service` | `configs/comment-service.yml` |
| `like-service` | `configs/like-service.yml` |
| `follow-service` | `configs/follow-service.yml` |
| `notification-service` | `configs/notification-service.yml` |
| `media-service` | `configs/media-service.yml` |
| `search-service` | `configs/search-service.yml` |

### What Each Config File Contains

**`configs/auth-service.yml`**
```yaml
# DB, JWT, OTP settings for auth-service
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/connectsphere_auth?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION}

otp:
  expiry-minutes: 10
  length: 6
```

**`configs/post-service.yml`** (and similarly comment, like, follow)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/connectsphere_post?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

**`configs/notification-service.yml`** — also has mail config
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/connectsphere_notification?...
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**`configs/media-service.yml`** — also has file upload limits and story expiry
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/connectsphere_media?...
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

story:
  expiry-hours: 24    # ConnectSphere stories expire after 24 hours
```

### How to Verify Config Server Is Working

Hit this URL in your browser or Postman after starting it:

```
GET http://localhost:8888/auth-service/default
```

You should get a JSON response containing the merged properties for `auth-service`. If you see the DB URL, JWT config etc., the Config Server is serving correctly.

Similarly:
```
GET http://localhost:8888/post-service/default
GET http://localhost:8888/comment-service/default
```

### `pom.xml` — Key Dependencies

```xml
<!-- Makes this a Config Server -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-config-server</artifactId>
</dependency>

<!-- Makes this a Eureka Client (registers with Eureka) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

---

## 6. API Gateway

### What It Is

The API Gateway is the **only service that clients (frontend, Postman, mobile app) talk to**. It runs on port `8080`. Clients never call `localhost:8081` (auth), `localhost:8082` (post) etc. directly. Every request goes through `localhost:8080`, and the Gateway decides where to forward it.

The Gateway does two things for every request:
1. **JWT Validation** — If the route requires authentication, the `JwtAuthenticationFilter` extracts the Bearer token, validates it against the same `JWT_SECRET` used by auth-service, and either forwards or rejects the request.
2. **Routing** — Based on the URL path, forwards the request to the correct microservice using Eureka's load-balanced `lb://service-name` URI.

### Package Structure

```
api-gateway/
└── src/main/java/com/connectsphere/gateway/
│   ├── ApiGatewayApplication.java
│   └── filter/
│       ├── JwtAuthenticationFilter.java   ← Validates JWT on protected routes
│       └── LoggingFilter.java             ← Logs every request/response globally
└── src/main/resources/
    └── application.yml                    ← All routing rules defined here
```

### `ApiGatewayApplication.java` — Explained

```java
@SpringBootApplication
@EnableDiscoveryClient   // ← Registers with Eureka AND fetches service list from Eureka
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

No `@EnableWebMvc` or `spring-boot-starter-web` here. The Gateway uses **Spring WebFlux** (reactive), not traditional Spring MVC. This is intentional — the gateway needs to handle thousands of concurrent connections efficiently.

> **Important:** Do NOT add `spring-boot-starter-web` to the gateway's `pom.xml`. It conflicts with the reactive stack that `spring-cloud-starter-gateway` requires.

### `application.yml` — Routing Rules Explained

The entire routing logic lives in `application.yml`. Here's how to read it:

```yaml
spring:
  cloud:
    gateway:
      routes:

        # Each route has:
        #   id       — a unique name (just for your reference)
        #   uri      — where to forward the request (lb:// = load-balanced via Eureka)
        #   predicates — which incoming URLs match this route
        #   filters  — what to do before forwarding (e.g., validate JWT)

        # AUTH SERVICE — Public routes (no JWT needed)
        - id: auth-service-public
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/register,
                   /api/v1/auth/login,
                   /api/v1/auth/verify-otp,
                   /api/v1/auth/refresh,
                   /api/v1/auth/forgot-password,
                   /api/v1/auth/reset-password,
                   /api/v1/auth/oauth2/**,
                   /api/v1/oauth2/**,
                   /api/v1/login/oauth2/**
          # No JwtAuthenticationFilter here — these are public

        # AUTH SERVICE — Protected routes (JWT required)
        - id: auth-service-protected
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - JwtAuthenticationFilter   # ← JWT must be valid to proceed

        # POST SERVICE — Public (guest feed browsing)
        - id: post-service-public
          uri: lb://post-service
          predicates:
            - Path=/api/v1/posts/public/**,
                   /api/v1/posts/search

        # POST SERVICE — Protected
        - id: post-service-protected
          uri: lb://post-service
          predicates:
            - Path=/api/v1/posts/**
          filters:
            - JwtAuthenticationFilter

        # All other services follow the same pattern...
        - id: comment-service
          uri: lb://comment-service
          predicates:
            - Path=/api/v1/comments/**
          filters:
            - JwtAuthenticationFilter

        - id: like-service
          uri: lb://like-service
          predicates:
            - Path=/api/v1/likes/**
          filters:
            - JwtAuthenticationFilter

        - id: follow-service
          uri: lb://follow-service
          predicates:
            - Path=/api/v1/follows/**
          filters:
            - JwtAuthenticationFilter

        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - JwtAuthenticationFilter

        # MEDIA/STORY — Public stories are accessible without login
        - id: media-service-public
          uri: lb://media-service
          predicates:
            - Path=/api/v1/stories/public/**

        - id: media-service-protected
          uri: lb://media-service
          predicates:
            - Path=/api/v1/media/**,
                   /api/v1/stories/**
          filters:
            - JwtAuthenticationFilter

        # SEARCH — No JWT needed (guests can search too, per case study)
        - id: search-service
          uri: lb://search-service
          predicates:
            - Path=/api/v1/search/**,
                   /api/v1/hashtags/**

      # CORS — allowed origins for browser clients
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"    # React dev server
              - "http://localhost:8090"    # connectsphere-web (Thymeleaf)
              - "http://localhost:5173"    # Vite dev server
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
```

**How `lb://auth-service` works:**  
The `lb://` prefix tells Spring Cloud Gateway to use Eureka to find the actual address of `auth-service`. It looks up the registry, gets the IP and port (`192.168.1.5:8081`), and forwards the request there. If multiple instances of `auth-service` are running, it load-balances between them automatically.

### `JwtAuthenticationFilter.java` — Explained

This filter is applied only to routes that have `- JwtAuthenticationFilter` in their `filters` list.

```java
@Component
@Slf4j
public class JwtAuthenticationFilter
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;   // MUST be the same secret as auth-service

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // 1. Read the Authorization header
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // 2. If missing or not "Bearer ...", return 401 immediately
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onUnauthorized(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);  // Strip "Bearer "

            try {
                // 3. Parse and validate the JWT
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // 4. Extract userId (subject) and role from token claims
                String userId = claims.getSubject();
                String role   = claims.get("role", String.class);

                // 5. Forward these as headers to the downstream service
                //    Now post-service, comment-service etc. can read who made the request
                //    without having to validate the JWT themselves
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(r -> r
                            .header("X-User-Id", userId)
                            .header("X-User-Role", role)
                        )
                        .build();

                return chain.filter(mutatedExchange);  // Forward the request

            } catch (Exception e) {
                // Token expired, tampered, or invalid → 401
                return onUnauthorized(exchange, "Invalid or expired token");
            }
        };
    }
}
```

**What downstream services receive:**  
After the Gateway validates the JWT, it strips the need for business services to do their own validation. They just read the headers:

```java
// In post-service, comment-service etc. controllers:
@GetMapping("/feed")
public ResponseEntity<?> getFeed(
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader("X-User-Role") String role) {
    // userId and role are already validated by the Gateway
}
```

### `LoggingFilter.java` — Explained

This is a **global filter** — it runs for every single request, whether protected or public, without being configured in any route.

```java
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();

        log.info(">>> Incoming Request: {} {}", method, path);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            int status = exchange.getResponse().getStatusCode().value();
            log.info("<<< Response: {} {} → {}", method, path, status);
        }));
    }

    @Override
    public int getOrder() {
        return -1;  // Run BEFORE JwtAuthenticationFilter (lower number = higher priority)
    }
}
```

Every time a request hits the Gateway, you'll see this in the logs:
```
>>> Incoming Request: POST /api/v1/auth/login
<<< Response: POST /api/v1/auth/login → 200
```

### `pom.xml` — Key Dependencies Explained

```xml
<!-- Gateway itself — uses reactive WebFlux under the hood -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>

<!-- Eureka Client — finds service addresses via lb:// routing -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<!-- Config Client — fetches jwt.secret from Config Server -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>

<!-- Reactor Netty — the embedded reactive web server for Gateway -->
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty-http</artifactId>
</dependency>

<!-- JWT — for validating tokens in JwtAuthenticationFilter -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

---

## 7. How All Three Work Together — Request Flow

Here is exactly what happens when a user calls `POST /api/v1/posts` (create a post):

```
Client (Postman / Browser)
  │
  │  POST http://localhost:8080/api/v1/posts
  │  Authorization: Bearer eyJhbGciOiJ...
  │
  ▼
API Gateway (port 8080)
  │
  ├── LoggingFilter runs first (order = -1)
  │   Logs: ">>> Incoming Request: POST /api/v1/posts"
  │
  ├── Route matched: id = post-service-protected
  │   Predicate: Path=/api/v1/posts/** ✓
  │
  ├── JwtAuthenticationFilter runs (because this route has it in filters)
  │   - Reads "Authorization: Bearer eyJhbGciOiJ..."
  │   - Parses JWT using JWT_SECRET
  │   - Extracts: userId="42", role="USER"
  │   - Adds headers: X-User-Id: 42, X-User-Role: USER
  │
  ├── Asks Eureka: "Where is post-service?"
  │   Eureka replies: "192.168.1.5:8082"
  │
  ▼
post-service (port 8082)
  │
  ├── SecurityConfig allows request (JWT already validated by Gateway)
  ├── PostResource.createPost() reads @RequestHeader("X-User-Id") = "42"
  ├── PostServiceImpl creates the post with authorId = 42
  │
  ▼
Response flows back through Gateway → Client

LoggingFilter logs: "<<< Response: POST /api/v1/posts → 201"
```

**Config Server's role in this flow:**  
Before any of this happens, at startup, `post-service` called Config Server and got its DB URL, credentials etc. The API Gateway called Config Server and got `jwt.secret`. Config Server called Eureka first to register itself. Everything builds on top of Eureka.

---

## 8. Port Map — All Services

| Service | Port | Database |
|---|---|---|
| **eureka-server** | `8761` | None |
| **config-server** | `8888` | None |
| **api-gateway** | `8080` | None |
| auth-service | `8081` | `connectsphere_auth` |
| post-service | `8082` | `connectsphere_post` |
| comment-service | `8083` | `connectsphere_comment` |
| like-service | `8084` | `connectsphere_like` |
| follow-service | `8085` | `connectsphere_follow` |
| notification-service | `8086` | `connectsphere_notification` |
| media-service | `8087` | `connectsphere_media` |
| search-service | `8088` | `connectsphere_search` |

All client traffic enters through `8080` only. Ports `8081`–`8088` are internal.

---

## 9. Common Errors and Fixes

### `com.netflix.discovery.shared.transport.TransportException: Cannot execute request on any known server`

**Cause:** A service is trying to register with Eureka but Eureka isn't running yet.  
**Fix:** Start Eureka Server first. Wait until you see the dashboard at `http://localhost:8761` before starting other services.

---

### `Could not resolve placeholder '${DB_USERNAME}'`

**Cause:** Environment variables not set before starting the service.  
**Fix:** Set `DB_USERNAME`, `DB_PASSWORD` etc. in your IDE's run configuration or terminal before running `mvn spring-boot:run`.

---

### `401 Unauthorized` on every protected request

**Cause:** `jwt.secret` in the API Gateway doesn't match the one in auth-service.  
**Fix:** Make sure `JWT_SECRET` environment variable is set to the exact same value everywhere. They must be byte-for-byte identical.

---

### `Failed to connect to config server` on service startup

**Cause:** Config Server is not running, or not reachable on port `8888`.  
**Fix:** Check Config Server is up. Because all services use `optional:configserver:`, they will still start using their local `application.yml` — but warn you in the logs. This is fine for development.

---

### `javax.ws.rs.ServiceUnavailableException` or `503 Service Unavailable` from Gateway

**Cause:** The target service (e.g., `post-service`) hasn't registered with Eureka yet, or crashed.  
**Fix:** Check `http://localhost:8761` to see if the service appears. If not, check its logs for startup errors.

---

### `spring-webmvc` conflict in API Gateway

**Cause:** Someone added `spring-boot-starter-web` to `api-gateway/pom.xml`.  
**Fix:** Remove it. The Gateway uses WebFlux (reactive). Adding the servlet-based web starter causes a conflict and breaks routing entirely.

---

### Routes not matching — requests returning 404

**Cause:** Route `predicates` path doesn't match what the client is sending.  
**Fix:** The client must call `http://localhost:8080/api/v1/posts/...` (through Gateway). The Gateway strips nothing — the full path `/api/v1/posts/...` is forwarded as-is to post-service, which also has `context-path: /api/v1`. This means post-service internally sees the request at `/api/v1/posts/...` which matches its `@RequestMapping("/posts")` correctly.

---
