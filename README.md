# post-service — ConnectSphere

**Microservice:** `post-service`  
**Port:** `8082`  
**Base URL:** `http://localhost:8082/api/v1`  
**Database:** `connectsphere_post` (MySQL)  
**Part of:** ConnectSphere Social Media Mini Platform

---

## What This Service Does

The post-service manages the complete lifecycle of user posts on ConnectSphere.  
It is one of eight independent microservices in the platform.

Responsibilities:
- Create, edit, soft-delete posts (text or media)
- Enforce visibility rules: PUBLIC, FOLLOWERS_ONLY, PRIVATE
- Serve the personalised news feed from followed users
- Guest browsing of public posts (no login needed)
- Full-text keyword search across public posts
- Maintain denormalised counters: likes, comments, shares (incremented by other services)
- Change post visibility after creation
- Provide post count for user profile badge

---

## Architecture — 5-Layer Structure

```
PostResource (Controller)          ← REST API layer, reads userId from JWT
    ↓
PostService (Interface)            ← Business contract
    ↓
PostServiceImpl (Implementation)   ← All business logic lives here
    ↓
PostRepository (JPA Interface)     ← Custom JPQL queries
    ↓
MySQL DB (connectsphere_post)      ← posts + post_media_urls tables
```

Cross-cutting: `LoggingAspect` (AOP) logs entry, exit, exceptions, and execution time
across all three layers automatically.

---

## Project Structure

```
post-service/
├── src/main/java/com/connectsphere/post/
│   ├── PostServiceApplication.java        ← Spring Boot entry point
│   ├── aop/
│   │   └── LoggingAspect.java             ← AOP logging (entry, exit, timing, exceptions)
│   ├── config/
│   │   ├── ApplicationConfig.java         ← App-level beans placeholder
│   │   └── SecurityConfig.java            ← Spring Security — defines public vs protected endpoints
│   ├── controller/
│   │   └── PostResource.java              ← All REST endpoints
│   ├── dto/
│   │   ├── ApiResponseDTO.java            ← Generic response wrapper {success, message, data, timestamp}
│   │   ├── CreatePostRequestDTO.java      ← Request body for POST /posts
│   │   ├── UpdatePostRequestDTO.java      ← Request body for PUT /posts/{id}
│   │   └── PostResponseDTO.java           ← What every post endpoint returns
│   ├── entity/
│   │   ├── Post.java                      ← JPA entity mapped to 'posts' table
│   │   ├── PostType.java                  ← Enum: TEXT / IMAGE / VIDEO
│   │   └── Visibility.java                ← Enum: PUBLIC / FOLLOWERS_ONLY / PRIVATE
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java    ← Converts exceptions to proper HTTP responses
│   │   ├── PostNotFoundException.java     ← Thrown when post not found / soft-deleted
│   │   └── UnauthorizedActionException.java ← Thrown when user tries to edit someone else's post
│   ├── repository/
│   │   └── PostRepository.java            ← JPA queries (feed, search, counters, soft-delete)
│   ├── security/
│   │   └── JwtAuthenticationFilter.java   ← Validates JWT from auth-service; sets userId in request
│   └── service/
│       ├── PostService.java               ← Interface (business contract)
│       └── PostServiceImpl.java           ← Full implementation
├── src/main/resources/
│   └── application.yml                    ← Port, DB URL, JWT secret, Eureka config
└── pom.xml                                ← Dependencies
```

---

## Database

**Database name:** `connectsphere_post`  
Created automatically on first startup (`createDatabaseIfNotExist=true`).

### Tables Created by Hibernate

**`posts` table**

| Column | Type | Notes |
|---|---|---|
| post_id | INT PK AUTO_INCREMENT | Primary key |
| author_id | INT NOT NULL | Cross-service ref to users.user_id in auth-service |
| content | TEXT NOT NULL | Post body (max 2000 chars) |
| post_type | VARCHAR(10) | TEXT / IMAGE / VIDEO |
| visibility | VARCHAR(20) | PUBLIC / FOLLOWERS_ONLY / PRIVATE |
| likes_count | INT DEFAULT 0 | Denormalised counter |
| comments_count | INT DEFAULT 0 | Denormalised counter |
| shares_count | INT DEFAULT 0 | Denormalised counter |
| is_deleted | BOOLEAN DEFAULT false | Soft-delete flag |
| created_at | DATETIME | Auto-set on INSERT |
| updated_at | DATETIME | Auto-updated on UPDATE |

**`post_media_urls` table** (child of posts)

| Column | Type | Notes |
|---|---|---|
| post_id | INT FK | References posts.post_id |
| media_url | VARCHAR(500) | CDN URL of attached media |

**Indexes:** `idx_author_id`, `idx_visibility`, `idx_created_at`, `idx_is_deleted`

---

## Environment Variables Required

Set these before starting the service:

```bash
# Windows
setx DB_USERNAME "root"
setx DB_PASSWORD "yourpassword"
setx JWT_SECRET "your-256-bit-secret-must-match-auth-service-exactly"

# Linux / Mac
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export JWT_SECRET=your-256-bit-secret-must-match-auth-service-exactly
```

> The `JWT_SECRET` must be **identical** to the one used in `auth-service`.  
> This service only validates tokens — it never issues them.

---

## How to Run

**Prerequisites:** Java 17, MySQL running, auth-service running on port 8081.

```bash
cd post-service
./mvnw spring-boot:run
```

Service starts on: `http://localhost:8082`  
Swagger UI: `http://localhost:8082/api/v1/swagger-ui/index.html`  
Health check: `http://localhost:8082/api/v1/actuator/health`

---

## How JWT Authentication Works in This Service

This service does NOT issue JWTs — that is auth-service's job.

Flow for every protected request:
1. Frontend sends `Authorization: Bearer <token>` header
2. `JwtAuthenticationFilter` intercepts the request
3. It validates the token signature using the same `JWT_SECRET` as auth-service
4. It extracts `userId`, `email`, `role` from the token claims
5. It stores `userId` and `role` as request attributes
6. The controller reads `request.getAttribute("requestingUserId")` — never from the body
7. This prevents any user from spoofing their own userId

---

## All API Endpoints

### Public Endpoints (No JWT Required)

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/v1/posts/public` | Browse all PUBLIC posts (guest feed) |
| GET | `/api/v1/posts/{postId}` | View a single post by ID |
| GET | `/api/v1/posts/user/{authorId}` | All posts by a specific user |
| GET | `/api/v1/posts/search?keyword=java` | Search posts by keyword (PUBLIC only) |
| GET | `/api/v1/posts/count/{authorId}` | Get total post count for a user |

### Protected Endpoints (JWT Required)

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/api/v1/posts` | Create a new post |
| PUT | `/api/v1/posts/{postId}` | Update post content (author only) |
| DELETE | `/api/v1/posts/{postId}` | Soft-delete post (author or admin) |
| PATCH | `/api/v1/posts/{postId}/visibility` | Change visibility (author only) |
| GET | `/api/v1/posts/feed?followeeIds=1,2,3` | Personalised news feed |

### Internal Endpoints (Called by Other Microservices)

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/api/v1/posts/{postId}/likes/increment` | Increment likes — called by like-service |
| POST | `/api/v1/posts/{postId}/likes/decrement` | Decrement likes — called by like-service |
| POST | `/api/v1/posts/{postId}/comments/increment` | Increment comments — called by comment-service |
| POST | `/api/v1/posts/{postId}/comments/decrement` | Decrement comments — called by comment-service |
| POST | `/api/v1/posts/{postId}/shares/increment` | Increment shares — called on repost |

---

## Request / Response Shapes

### Create Post — `POST /api/v1/posts`

**Request Body:**
```json
{
  "content": "Hello ConnectSphere! #firstpost @vikash",
  "mediaUrls": [],
  "postType": "TEXT",
  "visibility": "PUBLIC"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Post created successfully",
  "data": {
    "postId": 1,
    "authorId": 5,
    "content": "Hello ConnectSphere! #firstpost @vikash",
    "mediaUrls": [],
    "postType": "TEXT",
    "visibility": "PUBLIC",
    "likesCount": 0,
    "commentsCount": 0,
    "sharesCount": 0,
    "createdAt": "2026-04-19T10:30:00",
    "updatedAt": "2026-04-19T10:30:00"
  },
  "timestamp": "2026-04-19T10:30:00"
}
```

### Update Post — `PUT /api/v1/posts/{postId}`

**Request Body:**
```json
{
  "content": "Updated content here"
}
```

### Change Visibility — `PATCH /api/v1/posts/{postId}/visibility?visibility=PRIVATE`

No body needed. Pass visibility as query param.  
Allowed values: `PUBLIC`, `FOLLOWERS_ONLY`, `PRIVATE`

### Get Feed — `GET /api/v1/posts/feed?followeeIds=1&followeeIds=2&followeeIds=3`

No body. Pass followeeIds as repeated query params.

### Error Responses

```json
{
  "success": false,
  "message": "Post not found with id: 99",
  "timestamp": "2026-04-19T10:30:00"
}
```

| Scenario | HTTP Status |
|---|---|
| Post not found / soft-deleted | 404 |
| User tries to edit someone else's post | 403 |
| Invalid visibility value | 400 |
| Validation error (blank content) | 400 |
| Unexpected server error | 500 |

---

## Key Design Decisions

**Soft Delete:** Posts are never hard-deleted. `isDeleted = true` hides them from all
queries but preserves the record for a 30-day audit trail (ConnectSphere NFR).

**Cross-Service counters:** `likesCount`, `commentsCount`, `sharesCount` are denormalised.
Instead of doing a `COUNT(*)` JOIN across databases on every feed render, other services
call the increment/decrement endpoints atomically. This is the standard pattern for
microservices with separate databases.

**authorId from JWT only:** The controller never accepts `authorId` in the request body.
It always reads it from the JWT token via `request.getAttribute("requestingUserId")`.
This prevents identity spoofing.

**feedByUserIds from follow-service:** This service does not know about the follow graph.
The caller (frontend or API gateway) is responsible for calling follow-service to get the
list of followeeIds and then passing them to `GET /posts/feed?followeeIds=...`.

**Visibility query:** The `findFeedByUserIds` JPQL query uses string literals
`'PUBLIC', 'FOLLOWERS_ONLY'` instead of enum references because JPQL `IN` with enums
stored as strings requires this approach with the current Hibernate setup.

---

## Entities and DTOs Quick Reference

### Post Entity (stored in DB)
```
postId, authorId, content, mediaUrls (List), postType,
visibility, likesCount, commentsCount, sharesCount,
isDeleted, createdAt, updatedAt
```

### PostResponseDTO (returned in API responses)
```
postId, authorId, content, mediaUrls, postType,
visibility, likesCount, commentsCount, sharesCount,
createdAt, updatedAt
```
`isDeleted` is intentionally excluded from all API responses.

### CreatePostRequestDTO (accepted on create)
```
content   — required, 1 to 2000 characters
mediaUrls — optional, defaults to empty list
postType  — optional, defaults to TEXT
visibility — optional, defaults to PUBLIC
```

---

## Dependencies (pom.xml Summary)

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST API with embedded Tomcat |
| spring-boot-starter-security | JWT-based authentication |
| spring-boot-starter-data-jpa | Database access via Hibernate |
| spring-boot-starter-validation | @NotBlank, @Size on DTOs |
| spring-boot-starter-aop | AOP logging aspect |
| spring-cloud-starter-netflix-eureka-client | Service registration in Eureka |
| spring-cloud-starter-config | Fetch config from Config Server |
| mysql-connector-j | MySQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | JWT validation (v0.12.3) |
| lombok | Boilerplate reduction |
| spring-boot-starter-actuator | Health check endpoints |
| springdoc-openapi-starter-webmvc-ui | Swagger UI (v2.3.0) |

---

## Related Services in ConnectSphere

| Service | Port | Interacts With Post-Service How |
|---|---|---|
| auth-service | 8081 | Issues JWT tokens that post-service validates |
| like-service | 8083 | Calls `/posts/{id}/likes/increment` and `/decrement` |
| comment-service | 8084 | Calls `/posts/{id}/comments/increment` and `/decrement` |
| follow-service | 8085 | Provides followeeIds list for `GET /posts/feed` |
| search-service | 8086 | Calls `/posts/{id}` to index post content for hashtags |

