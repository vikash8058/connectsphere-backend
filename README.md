# comment-service — ConnectSphere

**Microservice:** `comment-service`  
**Port:** `8083`  
**Base URL:** `http://localhost:8083/api/v1`  
**Database:** `connectsphere_comment` (MySQL)  
**Part of:** ConnectSphere Social Media Mini Platform

---

## What This Service Does

The comment-service handles all threaded discussions on posts in ConnectSphere.  
It is one of eight independent microservices in the platform.

Responsibilities:
- Add top-level comments on posts
- Add replies to existing comments (two-level threading)
- Edit and soft-delete comments (author or admin only)
- Like and unlike comments (atomic counter updates)
- Get all comments for a post, or only top-level ones
- Get replies for a specific comment
- Get all comments written by a user
- Get comment count for post badge display
- Verify post existence before allowing a comment (via FeignClient to post-service)
- Notify post-service to increment/decrement `commentsCount` after add/delete

---

## Architecture — 5-Layer Structure

```
CommentResource (Controller)       <- REST API layer, reads userId from JWT
    |
CommentService (Interface)         <- Business contract
    |
CommentServiceImpl (Implementation)<- All business logic lives here
    |
CommentRepository (JPA Interface)  <- Custom JPQL queries
    |
MySQL DB (connectsphere_comment)   <- comments table
```

Cross-cutting:
- `JwtAuthenticationFilter` — validates JWT, stores userId + role in request attributes
- `PostClient` (FeignClient) — calls post-service to verify posts and update counters
- `GlobalExceptionHandler` — converts all exceptions to consistent JSON responses

---

## How This Service Differs From post-service

The comment-service introduces one new concept that post-service does not have:
**inter-service communication via FeignClient.**

Before saving any comment, this service calls post-service to:
1. Verify the post actually exists (`GET /posts/{postId}`)
2. Block comments on soft-deleted posts
3. Increment `commentsCount` on the post after a comment is added
4. Decrement `commentsCount` on the post after a comment is deleted

This keeps the post's `commentsCount` always in sync without doing expensive
`COUNT(*)` queries across databases.

---

## Two-Level Threading Model

```
Post
 |-- Comment A  (parentCommentId = null)       <- top-level comment
 |    |-- Reply 1  (parentCommentId = A)       <- reply to Comment A
 |    |-- Reply 2  (parentCommentId = A)       <- reply to Comment A
 |-- Comment B  (parentCommentId = null)       <- top-level comment
      |-- Reply 3  (parentCommentId = B)       <- reply to Comment B
```

Only two levels are supported as per the ConnectSphere case study (section 4.3).
You cannot reply to a reply — `parentCommentId` always points to a top-level comment.

---

## Soft-Delete Behaviour

When a comment is deleted, `isDeleted` is set to `true` — the record stays in the DB.

In API responses, the `toDTO()` method replaces the content of deleted comments:
```
"content": "[This comment was deleted]"
```

This preserves thread structure — if Comment A has replies and is deleted,
the replies still show under it. The thread is not broken.

---

## Project Structure

```
comment-service/
|-- src/main/java/com/connectsphere/comment/
|   |-- CommentServiceApplication.java         <- Spring Boot entry point
|   |-- aop/
|   |   |-- LoggingAspect.java                 <- AOP logging (entry, exit, timing, exceptions)
|   |-- client/
|   |   |-- PostClient.java                    <- FeignClient — calls post-service REST APIs
|   |-- config/
|   |   |-- ApplicationConfig.java             <- RestTemplate bean
|   |   |-- SecurityConfig.java                <- Public vs protected endpoint rules
|   |-- controller/
|   |   |-- CommentResource.java               <- All REST endpoints
|   |-- dto/
|   |   |-- ApiResponseDTO.java                <- Generic wrapper {success, message, data, timestamp}
|   |   |-- AddCommentRequestDTO.java          <- Request body for POST /comments
|   |   |-- UpdateCommentRequestDTO.java       <- Request body for PUT /comments/{id}
|   |   |-- CommentResponseDTO.java            <- Returned by all comment endpoints
|   |   |-- PostResponseDTO.java               <- Used to deserialize post-service response
|   |-- entity/
|   |   |-- Comment.java                       <- JPA entity mapped to 'comments' table
|   |-- exception/
|   |   |-- GlobalExceptionHandler.java        <- Central error handler
|   |   |-- CommentNotFoundException.java      <- Comment not found / soft-deleted
|   |   |-- PostNotFoundException.java         <- Post not found in post-service
|   |   |-- PostServiceUnavailableException.java <- post-service is down (503)
|   |   |-- UnauthorizedActionException.java   <- User modifying someone else's comment
|   |-- repository/
|   |   |-- CommentRepository.java             <- All DB queries
|   |-- security/
|   |   |-- JwtAuthenticationFilter.java       <- JWT validation, sets userId in request
|   |-- service/
|       |-- CommentService.java                <- Interface (business contract)
|       |-- CommentServiceImpl.java            <- Full implementation
|-- src/main/resources/
|   |-- application.yml                        <- Port, DB, JWT, Feign, Eureka config
|-- pom.xml                                    <- Dependencies
```

---

## Database

**Database name:** `connectsphere_comment`  
Created automatically on first startup (`createDatabaseIfNotExist=true`).

### `comments` Table

| Column | Type | Notes |
|---|---|---|
| comment_id | INT PK AUTO_INCREMENT | Primary key |
| post_id | INT NOT NULL | Cross-service ref to posts.post_id in post-service |
| author_id | INT NOT NULL | Cross-service ref to users.user_id in auth-service |
| parent_comment_id | INT NULL | null = top-level, value = reply to this commentId |
| content | TEXT NOT NULL | Text body of the comment |
| likes_count | INT DEFAULT 0 | Denormalised like counter |
| is_deleted | BOOLEAN DEFAULT false | Soft-delete flag |
| created_at | DATETIME | Auto-set on INSERT |
| updated_at | DATETIME | Auto-updated on UPDATE |

**Indexes:** `idx_post_id`, `idx_author_id`, `idx_parent_comment_id`, `idx_is_deleted`

> There are **no foreign keys** to other services' databases. `post_id` and `author_id`
> are plain integer columns. Cross-service integrity is enforced via FeignClient calls
> at the application layer, not at the DB layer.

---

## FeignClient — PostClient

`PostClient.java` is the inter-service communication layer.
It replaces `RestTemplate` with the cleaner OpenFeign declarative approach.

```java
@FeignClient(name = "post-service", url = "${post-service.base-url}")
public interface PostClient {

    @GetMapping("/posts/{postId}")
    PostResponseDTO getPostById(@PathVariable Integer postId);

    @PostMapping("/posts/{postId}/comments/increment")
    void incrementCommentCount(@PathVariable Integer postId);

    @PostMapping("/posts/{postId}/comments/decrement")
    void decrementCommentCount(@PathVariable Integer postId);
}
```

**Why `PostResponseDTO` directly (not `ApiResponseDTO<PostResponseDTO>`)?**

Feign uses Jackson to deserialize. Java's generic type erasure means Feign cannot
resolve `ApiResponseDTO<PostResponseDTO>` at runtime — it would deserialize the `data`
field as `LinkedHashMap` instead of `PostResponseDTO`. Returning the concrete type
directly solves this completely.

**Failure handling:**

| Scenario | Exception Thrown | HTTP Response |
|---|---|---|
| Post not found (404 from post-service) | `PostNotFoundException` | 404 |
| post-service is down / unreachable | `PostServiceUnavailableException` | 503 |
| Comment is on a soft-deleted post | `PostNotFoundException` | 404 |

Counter calls (`increment`/`decrement`) are fire-and-forget — wrapped in try-catch.
If post-service is temporarily down, the comment is still saved.
Counts are eventually consistent.

---

## Environment Variables Required

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

> `JWT_SECRET` must be **identical** to auth-service and post-service.
> This service only validates tokens — it never issues them.

---

## How to Run

**Prerequisites:** Java 17, MySQL running, auth-service on 8081, post-service on 8082.

```bash
cd comment-service
./mvnw spring-boot:run
```

Service starts on: `http://localhost:8083`  
Swagger UI: `http://localhost:8083/api/v1/swagger-ui/index.html`  
Health check: `http://localhost:8083/api/v1/actuator/health`

---

## All API Endpoints

### Public Endpoints (No JWT Required)

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/v1/comments/post/{postId}` | All comments for a post (incl. soft-deleted with placeholder) |
| GET | `/api/v1/comments/post/{postId}/top-level` | Top-level comments only (no replies) |
| GET | `/api/v1/comments/{commentId}` | Single comment by ID |
| GET | `/api/v1/comments/{commentId}/replies` | All replies to a comment |
| GET | `/api/v1/comments/count/{postId}` | Comment count for post badge |

### Protected Endpoints (JWT Required)

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/api/v1/comments` | Add a top-level comment or reply |
| PUT | `/api/v1/comments/{commentId}` | Update comment content (author only) |
| DELETE | `/api/v1/comments/{commentId}` | Soft-delete comment (author / admin / mod) |
| POST | `/api/v1/comments/{commentId}/like` | Like a comment |
| POST | `/api/v1/comments/{commentId}/unlike` | Unlike a comment |
| GET | `/api/v1/comments/user/{authorId}` | All comments by a user |

---

## Request / Response Shapes

### Add a Top-Level Comment — `POST /api/v1/comments`

**Request Body:**
```json
{
  "postId": 1,
  "parentCommentId": null,
  "content": "Great post! Really enjoyed reading this."
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Comment added successfully",
  "data": {
    "commentId": 10,
    "postId": 1,
    "authorId": 5,
    "parentCommentId": null,
    "content": "Great post! Really enjoyed reading this.",
    "likesCount": 0,
    "isDeleted": false,
    "createdAt": "2026-04-19T14:00:00",
    "updatedAt": "2026-04-19T14:00:00"
  },
  "timestamp": "2026-04-19T14:00:00"
}
```

### Add a Reply — `POST /api/v1/comments`

**Request Body:**
```json
{
  "postId": 1,
  "parentCommentId": 10,
  "content": "I agree with you!"
}
```

`parentCommentId` = 10 means this is a reply to comment 10.

### Update Comment — `PUT /api/v1/comments/{commentId}`

**Request Body:**
```json
{
  "content": "Updated: Great post! Learned a lot."
}
```

### Error Responses

```json
{
  "success": false,
  "message": "Comment not found with id: 99",
  "timestamp": "2026-04-19T14:00:00"
}
```

| Scenario | HTTP Status |
|---|---|
| Comment not found / soft-deleted | 404 |
| Post not found in post-service | 404 |
| post-service is down | 503 |
| User modifying someone else's comment | 403 |
| Blank content / validation error | 400 |
| Unexpected server error | 500 |

---

## Key Design Decisions

**authorId from JWT only:** The controller never reads `authorId` from the request body.
It always reads it from `request.getAttribute("requestingUserId")` which is set by
`JwtAuthenticationFilter` after JWT validation. This prevents identity spoofing.

**Post verification before comment:** Every `addComment()` call first hits post-service
via FeignClient to confirm the post exists and is not deleted. This prevents orphan
comments referencing non-existent posts.

**Fire-and-forget counter updates:** After a comment is saved or deleted, this service
calls post-service to increment/decrement `commentsCount`. This call is wrapped in
try-catch. If it fails, the comment operation still succeeds — the count is eventually
consistent, not strictly consistent.

**Soft delete preserves thread:** When a comment is deleted, the record stays in the
database. Replies to that comment still show up in the thread. The deleted comment's
content is replaced with `"[This comment was deleted]"` in the response. This mirrors
how Reddit, YouTube, and most social platforms handle thread deletion.

**FeignClient over RestTemplate:** This service uses OpenFeign instead of RestTemplate
for inter-service calls because it is cleaner and declarative. The `PostClient` interface
defines the contract — no manual URL building, no response parsing boilerplate.

---

## Entities and DTOs Quick Reference

### Comment Entity (stored in DB)
```
commentId, postId, authorId, parentCommentId,
content, likesCount, isDeleted, createdAt, updatedAt
```

### CommentResponseDTO (returned in API)
```
commentId, postId, authorId, parentCommentId,
content (replaced with placeholder if deleted),
likesCount, isDeleted, createdAt, updatedAt
```

### AddCommentRequestDTO (accepted on create)
```
postId          — required
parentCommentId — optional (null = top-level, value = reply)
content         — required, 1 to 1000 characters
```

### UpdateCommentRequestDTO (accepted on update)
```
content — required, 1 to 1000 characters
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
| spring-cloud-starter-openfeign | FeignClient for post-service calls |
| spring-cloud-starter-netflix-eureka-client | Service registration in Eureka |
| spring-cloud-starter-config | Fetch config from Config Server |
| mysql-connector-j | MySQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | JWT validation (v0.12.3) |
| lombok | Boilerplate reduction |
| spring-boot-starter-actuator | Health check endpoints |
| springdoc-openapi-starter-webmvc-ui | Swagger UI (v2.3.0) |

---

## Related Services in ConnectSphere

| Service | Port | Interacts With Comment-Service How |
|---|---|---|
| auth-service | 8081 | Issues JWT tokens that comment-service validates |
| post-service | 8082 | comment-service calls it to verify posts and update counters |
| like-service | 8084 | In full implementation, like-service handles comment likes too |

---

## ConnectSphere Case Study Reference

- Section 2.3 — Registered User Requirements (comment on posts, reply to comments, like comments, edit/delete own comments)
- Section 4.3 — Comment-Service Class Diagram
- Section 5 — Microservices Architecture Overview
- Section 6 — NFR: soft-delete 30-day retention, graceful degradation on partial failure
