# Media Service — ConnectSphere

Microservice responsible for all media upload and ephemeral story operations in the ConnectSphere social platform. Handles file upload simulation (CDN URL generation), media-to-post linking, soft deletion, and the full 24-hour story lifecycle including scheduled expiry.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.0 |
| Security | Spring Security + JWT |
| Database | MySQL 8 + Spring Data JPA / Hibernate |
| File Upload | Spring Multipart (`MultipartFile`) |
| Service Discovery | Netflix Eureka Client |
| Config Management | Spring Cloud Config |
| Scheduling | Spring `@Scheduled` (story expiry) |
| Documentation | SpringDoc OpenAPI (Swagger UI) |
| Monitoring | Spring Boot Actuator |
| Build Tool | Maven |
| AOP Logging | Spring AOP |

---

## Port & Context Path

```
Port         : 8085
Context Path : /api/v1
Base URL     : http://localhost:8085/api/v1
```

---

## Database

```
Database : connectsphere_media
Tables   : media, stories
```

### media table

| Column | Type | Description |
|---|---|---|
| media_id | INT (PK, AI) | Primary key |
| uploader_id | INT | References users.user_id in auth-service (no DB FK) |
| url | VARCHAR(1000) | Full CDN URL of the uploaded file |
| media_type | ENUM | IMAGE / VIDEO |
| size_kb | BIGINT | File size in kilobytes |
| mime_type | VARCHAR(50) | Actual MIME type (image/jpeg, image/png, image/webp, video/mp4) |
| linked_post_id | INT (nullable) | Post ID this media is attached to (set after post creation) |
| is_deleted | BOOLEAN | Soft delete flag |
| uploaded_at | DATETIME | Auto-set on INSERT |

### stories table

| Column | Type | Description |
|---|---|---|
| story_id | INT (PK, AI) | Primary key |
| author_id | INT | References users.user_id in auth-service (no DB FK) |
| media_url | VARCHAR(1000) | CDN URL of the story media |
| caption | VARCHAR(500) | Optional text caption |
| media_type | ENUM | IMAGE / VIDEO |
| views_count | INT | View counter (excludes author self-views) |
| expires_at | DATETIME | `created_at + 24 hours` — set at creation |
| created_at | DATETIME | Auto-set on INSERT |
| is_active | BOOLEAN | `false` after expiry or manual deletion |

---

## Environment Variables

```env
DB_USERNAME=your_mysql_username
DB_PASSWORD=your_mysql_password
JWT_SECRET=your_jwt_secret_key   # Must match auth-service exactly
```

---

## Project Structure

```
media-service/
├── src/main/java/com/connectsphere/media/
│   ├── MediaServiceApplication.java
│   ├── aop/
│   │   └── LoggingAspect.java               # Cross-cutting logs for all layers
│   ├── config/
│   │   ├── ApplicationConfig.java
│   │   └── SecurityConfig.java              # JWT filter + endpoint security rules
│   ├── controller/
│   │   └── MediaResource.java               # REST endpoints (media + stories)
│   ├── dto/
│   │   ├── ApiResponseDTO.java
│   │   ├── CreateStoryRequestDTO.java
│   │   ├── MediaResponseDTO.java
│   │   └── StoryResponseDTO.java
│   ├── entity/
│   │   ├── Media.java
│   │   ├── Story.java
│   │   └── MediaType.java                   # IMAGE, VIDEO
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── MediaNotFoundException.java
│   │   ├── StoryNotFoundException.java
│   │   ├── UnauthorizedActionException.java
│   │   └── UnsupportedMediaTypeException.java
│   ├── repository/
│   │   ├── MediaRepository.java
│   │   └── StoryRepository.java
│   ├── scheduler/
│   │   └── StoryExpiryScheduler.java        # Runs every 5 min, expires 24h stories
│   ├── security/
│   │   └── JwtAuthenticationFilter.java
│   └── service/
│       ├── MediaService.java
│       └── MediaServiceImpl.java
└── src/main/resources/
    └── application.yml
```

---

## API Endpoints

### Media Endpoints (all require JWT)

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/media/upload` | Upload image (JPEG/PNG/WebP) or video (MP4). Returns CDN URL. |
| GET | `/api/v1/media/{mediaId}` | Get media metadata by ID |
| GET | `/api/v1/media/post/{postId}` | Get all media linked to a post |
| GET | `/api/v1/media/uploader/{uploaderId}` | Get all media uploaded by a user |
| DELETE | `/api/v1/media/{mediaId}` | Soft-delete media (uploader or ADMIN/MODERATOR) |
| PATCH | `/api/v1/media/{mediaId}/link/{postId}` | Link uploaded media to a post |

### Internal Media Endpoint (inter-service, JWT required)

| Method | URL | Description |
|---|---|---|
| DELETE | `/api/v1/media/post/{postId}/soft-delete` | Soft-delete all media for a deleted post (called by post-service) |

### Story Endpoints (all require JWT)

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/stories` | Create a 24-hour ephemeral story |
| GET | `/api/v1/stories/feed?authorIds=1,2,3` | Get active stories from followed users |
| GET | `/api/v1/stories/{storyId}/view` | View story (increments view count if viewer ≠ author) |
| GET | `/api/v1/stories/user/{authorId}` | Get all active stories by a user |
| DELETE | `/api/v1/stories/{storyId}` | Delete story (author or ADMIN/MODERATOR) |

---

## Allowed File Types & Size Limits

| Type | Allowed MIME Types | Max Size |
|---|---|---|
| IMAGE | `image/jpeg`, `image/png`, `image/webp` | 10 MB (10240 KB) |
| VIDEO | `video/mp4` | 100 MB (102400 KB) |

---

## Request / Response Examples

### Upload Media

**Request** `POST /api/v1/media/upload` (multipart/form-data)
```
Key  : file
Type : File
Value: photo.jpg
```

**Response** `201 Created`
```json
{
  "success": true,
  "message": "Media uploaded successfully",
  "data": {
    "mediaId": 1,
    "uploaderId": 3,
    "url": "https://cdn.connectsphere.com/media/2026/04/uuid-abc123.jpg",
    "mediaType": "IMAGE",
    "sizeKb": 256,
    "mimeType": "image/jpeg",
    "linkedPostId": null,
    "uploadedAt": "2026-04-20T10:00:00"
  }
}
```

---

### Create Story

**Request** `POST /api/v1/stories`
```json
{
  "mediaUrl": "https://cdn.connectsphere.com/media/2026/04/uuid-abc123.jpg",
  "caption": "Good morning!",
  "mediaType": "IMAGE"
}
```

**Response** `201 Created`
```json
{
  "success": true,
  "message": "Story created successfully",
  "data": {
    "storyId": 1,
    "authorId": 3,
    "mediaUrl": "https://cdn.connectsphere.com/media/2026/04/uuid-abc123.jpg",
    "caption": "Good morning!",
    "mediaType": "IMAGE",
    "viewsCount": 0,
    "expiresAt": "2026-04-21T10:00:00",
    "createdAt": "2026-04-20T10:00:00",
    "isActive": true
  }
}
```

---

## Security Design

- JWT tokens are validated by `JwtAuthenticationFilter` on every request.
- `userId` and `role` are extracted from JWT and stored as request attributes (`requestingUserId`, `requestingUserRole`).
- The controller reads `userId` only from request attributes — **never from the request body** — preventing spoofing.
- Only the uploader/author or `ADMIN`/`MODERATOR` can delete media or stories.
- View count is **not incremented** when the story's own author views it.

---

## Story Expiry

Stories automatically expire 24 hours after creation. `StoryExpiryScheduler` runs every 5 minutes (cron: `0 */5 * * * *`) and calls `storyRepository.deactivateExpiredStories(now)` — a single batch `UPDATE` query. This satisfies the NFR: *"Stories are purged within 5 minutes of their 24-hour expiry."*

---

## Media Upload Flow

```
1. Client sends multipart POST /media/upload
2. MIME type validated against allowlist
3. File size validated against configurable limits
4. UUID-based unique filename generated
5. CDN URL built: {cdn-base-url}/{year}/{month}/{uuid}.ext
6. Media entity persisted (linkedPostId = null)
7. CDN URL returned → client uses it in CreatePostRequest.mediaUrls
8. Client later calls PATCH /media/{mediaId}/link/{postId} to associate
```

---

## Soft Delete

- **Media:** `isDeleted = true` — record retained for 30-day audit trail.
- **Story:** `isActive = false` — set on manual delete or scheduler expiry.
- When `post-service` deletes a post, it calls `DELETE /media/post/{postId}/soft-delete` to cascade the soft delete to all linked media.

---

## Running Locally

### Prerequisites
- Java 17
- MySQL 8 running on `localhost:3306`
- Eureka Server on `localhost:8761` (or disable in config)

### Steps

```bash
cd media-service

export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export JWT_SECRET=your_jwt_secret

./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### Swagger UI
```
http://localhost:8085/api/v1/swagger-ui.html
```

### Actuator Health
```
http://localhost:8085/api/v1/actuator/health
```

---

## Running Tests

```bash
./mvnw test
```

---

## Configuration Reference (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `media.max-image-size-kb` | 10240 | Max image upload size (10 MB) |
| `media.max-video-size-kb` | 102400 | Max video upload size (100 MB) |
| `media.allowed-image-types` | jpeg,png,webp | Allowed image MIME types |
| `media.allowed-video-types` | video/mp4 | Allowed video MIME types |
| `media.cdn-base-url` | https://cdn.connectsphere.com/media | CDN base URL for generated URLs |
| `story.expiry-check-cron` | `0 */5 * * * *` | Story expiry scheduler cron |

---

## Related Services

| Service | Interaction |
|---|---|
| auth-service | Issues JWT tokens validated here |
| post-service | Calls `DELETE /media/post/{postId}/soft-delete` on post deletion |
| follow-service | Provides `authorIds` consumed by `/stories/feed` |
| eureka-server | Service registration & discovery |
| config-server | Centralized config (optional in dev) |