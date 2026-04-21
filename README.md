  # Search / Hashtag Service — ConnectSphere

Complete documentation for the `search-service` microservice.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [How It Works — End to End](#3-how-it-works--end-to-end)
4. [Database Schema](#4-database-schema)
5. [REST API Endpoints](#5-rest-api-endpoints)
6. [RabbitMQ Configuration](#6-rabbitmq-configuration)
7. [Feign Clients](#7-feign-clients)
8. [Service Classes](#8-service-classes)
9. [post-service Changes](#9-post-service-changes)
10. [Security](#10-security)
11. [Non-Functional Notes](#11-non-functional-notes)

---

## 1. Overview

`search-service` is the dedicated microservice for hashtag indexing, trending computation, and full-text search on ConnectSphere. It owns its own MySQL database (`connectsphere_search`) and communicates with other services exclusively through RabbitMQ (async) and OpenFeign (sync).

**What it does:**
- Parses `#hashtag` tokens from post content using regex
- Upserts `Hashtag` records and creates `PostHashtag` mappings when a post is created
- Diffs old vs new hashtags when a post is updated
- Removes all mappings when a post is deleted
- Computes trending hashtags by `postCount DESC`
- Serves full-text post search and user search
- Provides hashtag autocomplete for the post creation input

**What it does NOT do:**
- Store post content or user data (those live in post-service and auth-service)
- Index non-PUBLIC posts (PRIVATE and FOLLOWERS_ONLY posts are never indexed)

Base package: `com.connectsphere.search`
Port: `8087`
DB: `connectsphere_search`
Context path: `/api/v1`

---

## 2. Architecture

```
post-service
  createPost()     ──┐
  updatePost()     ──┼──▶  connectsphere.post.exchange  ──▶  PostEventListener
  deletePost()     ──┘           (RabbitMQ)                        │
  changeVisibility()                                                ▼
                                                          SearchServiceImpl
                                                            indexPost()
                                                            reIndexPost()
                                                            removePostIndex()
                                                                   │
                                                                   ▼
                                                          connectsphere_search DB
                                                          (hashtags + post_hashtags)

Frontend/Gateway
  GET /search/posts?keyword=   ──▶  SearchServiceImpl.searchPosts()
  GET /search/users?query=     ──▶  AuthServiceClient (Feign → auth-service)
  GET /hashtags/trending       ──▶  HashtagRepository.findTrendingHashtags()
  GET /hashtags/{tag}/posts    ──▶  PostHashtagRepository → PostServiceClient (Feign)
```

---

## 3. How It Works — End to End

### POST_CREATED flow

```
1. User creates a post with content: "Hello #spring and #java!"

2. post-service.createPost()
      → saves Post to post_db
      → PostIndexPublisher.publishPostCreated(postId, authorId, content, "PUBLIC")
            → rabbitTemplate.convertAndSend(exchange, "post.created", message)
                                                              [async, fire and forget]

3. RabbitMQ delivers to connectsphere.search.post.created.queue

4. PostEventListener.handlePostCreated(event)
      → searchService.indexPost(postId, authorId, "Hello #spring and #java!", "PUBLIC")

5. SearchServiceImpl.indexPost()
      → extractHashtags("Hello #spring and #java!") → {"spring", "java"}
      → For "spring":
            hashtagRepository.findByTag("spring") → Optional.empty
            hashtagRepository.save(Hashtag{tag="spring", postCount=1})   ← new hashtag
            postHashtagRepository.save(PostHashtag{postId=42, hashtagId=1})
      → For "java":
            hashtagRepository.findByTag("java") → Optional.of(existing)
            hashtagRepository.incrementPostCount(existingId)              ← atomic UPDATE
            postHashtagRepository.save(PostHashtag{postId=42, hashtagId=5})

6. DB state:
      hashtags:      {id=1, tag="spring", postCount=1}
                     {id=5, tag="java",   postCount=51}    ← was 50, now 51
      post_hashtags: {postId=42, hashtagId=1}
                     {postId=42, hashtagId=5}
```

### POST_UPDATED flow

```
1. User edits post 42: removes #spring, adds #boot
   Old content: "Hello #spring and #java!"
   New content: "Hello #boot and #java!"

2. PostIndexPublisher.publishPostUpdated(42, authorId, newContent, oldContent, "PUBLIC")

3. SearchServiceImpl.reIndexPost()
      → oldTags = {"spring", "java"}
      → newTags = {"boot", "java"}
      → removedTags = {"spring"}   → deleteByPostId(42) + decrementPostCount(spring)
      → addedTags   = {"boot"}     → upsertHashtag("boot") + createPostHashtagMapping(42, boot)
      → "java" unchanged → nothing happens
```

### POST_DELETED flow

```
1. PostIndexPublisher.publishPostDeleted(42)

2. SearchServiceImpl.removePostIndex(42)
      → postHashtagRepository.findByPostId(42) → [spring mapping, java mapping]
      → hashtagRepository.decrementPostCount(spring_id)
      → hashtagRepository.decrementPostCount(java_id)
      → postHashtagRepository.deleteByPostId(42)
```

### Visibility changed to PRIVATE

```
1. PostIndexPublisher.publishPostUpdated(42, authorId, content, content, "PRIVATE")

2. SearchServiceImpl.reIndexPost()
      → visibility != "PUBLIC"
      → removePostIndex(42)   ← de-indexes completely, post disappears from search
```

### searchPosts() — keyword vs hashtag

```
GET /search/posts?keyword=spring boot
  → keyword does NOT start with "#"
  → searchByKeywordInternal("spring boot")
  → PostServiceClient.searchPosts("spring boot")   ← Feign call to post-service
  → enrich each result with hashtag tags from local DB
  → return List<PostSearchResultDTO>

GET /search/posts?keyword=#java
  → keyword starts with "#"
  → searchByHashtagInternal("java")
  → PostHashtagRepository.findPostIdsByHashtagTag("java")   ← local DB query
  → for each postId: PostServiceClient.getPostById(postId)  ← Feign enrich
  → return List<PostSearchResultDTO>
```

### getTrendingHashtags()

```
GET /hashtags/trending?limit=5
  → hashtagRepository.findTrendingHashtags(PageRequest.of(0, 5))
  → SELECT * FROM hashtags WHERE post_count > 0 ORDER BY post_count DESC LIMIT 5
  → return List<HashtagResponseDTO>
```

---

## 4. Database Schema

### `hashtags` table

| Column | Type | Notes |
|---|---|---|
| `hashtag_id` | INT PK | Auto-increment |
| `tag` | VARCHAR(100) UNIQUE | Lowercase, no # prefix. e.g. `springboot` |
| `post_count` | INT | Denormalised count — drives trending ranking |
| `last_used_at` | DATETIME | Auto-updated on every upsert |

Indexes: `idx_tag`, `idx_post_count DESC`, `idx_last_used_at`

### `post_hashtags` table

| Column | Type | Notes |
|---|---|---|
| `id` | INT PK | Auto-increment |
| `post_id` | INT | Cross-service reference to post-service (no DB FK) |
| `hashtag_id` | INT FK | References `hashtags.hashtag_id` |
| `created_at` | DATETIME | Auto-set on INSERT |

Unique constraint: `(post_id, hashtag_id)` — prevents duplicate mappings.
Indexes: `idx_post_id`, `idx_hashtag_id`

---

## 5. REST API Endpoints

Base path: `/api/v1` (all endpoints below are relative to this)

All endpoints are **public** — no JWT required. Guests can search (case study section 2.2).

### Search Endpoints

#### `GET /search/posts`

Search posts by keyword or hashtag.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `keyword` | String | Yes | Plain text or `#tag` prefix for hashtag search |

**Example — keyword search:**
```
GET /api/v1/search/posts?keyword=spring boot
```
```json
{
  "success": true,
  "message": "Search completed",
  "data": [
    {
      "postId": 42,
      "authorId": 7,
      "content": "Getting started with #spring #boot",
      "mediaUrls": [],
      "postType": "TEXT",
      "visibility": "PUBLIC",
      "likesCount": 12,
      "commentsCount": 3,
      "sharesCount": 1,
      "hashtags": ["#spring", "#boot"],
      "createdAt": "2026-04-20T10:30:00",
      "updatedAt": "2026-04-20T10:30:00"
    }
  ],
  "timestamp": "2026-04-21T09:00:00"
}
```

**Example — hashtag search:**
```
GET /api/v1/search/posts?keyword=#java
```
Same response shape — fetches all posts tagged with `#java`.

**Error — empty keyword:**
```json
{
  "success": false,
  "message": "Search keyword cannot be empty",
  "timestamp": "2026-04-21T09:00:00"
}
```

---

#### `GET /search/users`

Search users by username or fullName.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | String | Yes | Partial match on username or fullName |

**Example:**
```
GET /api/v1/search/users?query=rahul
```
```json
{
  "success": true,
  "message": "User search completed",
  "data": [
    {
      "userId": 5,
      "username": "rahul_dev",
      "fullName": "Rahul Kumar",
      "bio": "Java developer",
      "profilePicUrl": "https://cdn.connectsphere.com/pics/rahul.jpg",
      "role": "USER"
    }
  ],
  "timestamp": "2026-04-21T09:00:00"
}
```

**auth-service down — graceful degradation:**
```json
{
  "success": true,
  "message": "User search unavailable",
  "data": [],
  "timestamp": "2026-04-21T09:00:00"
}
```

---

### Hashtag Endpoints

#### `GET /hashtags/trending`

Get top N trending hashtags ordered by `postCount DESC`.

| Parameter | Type | Default | Max | Description |
|---|---|---|---|---|
| `limit` | int | `10` | `50` | Number of hashtags to return |

**Example:**
```
GET /api/v1/hashtags/trending?limit=5
```
```json
{
  "success": true,
  "message": "Trending hashtags fetched",
  "data": [
    { "hashtagId": 5,  "tag": "java",       "postCount": 1532, "lastUsedAt": "2026-04-21T08:55:00" },
    { "hashtagId": 1,  "tag": "spring",     "postCount": 987,  "lastUsedAt": "2026-04-21T08:50:00" },
    { "hashtagId": 12, "tag": "springboot", "postCount": 743,  "lastUsedAt": "2026-04-21T08:45:00" },
    { "hashtagId": 8,  "tag": "mysql",      "postCount": 512,  "lastUsedAt": "2026-04-21T08:40:00" },
    { "hashtagId": 3,  "tag": "docker",     "postCount": 408,  "lastUsedAt": "2026-04-21T08:35:00" }
  ],
  "timestamp": "2026-04-21T09:00:00"
}
```

---

#### `GET /hashtags/{tag}/posts`

Get all posts tagged with a specific hashtag.

| Path Variable | Type | Description |
|---|---|---|
| `tag` | String | Hashtag text (with or without `#` prefix — both work) |

**Example:**
```
GET /api/v1/hashtags/java/posts
GET /api/v1/hashtags/%23java/posts   (URL-encoded # also works)
```
```json
{
  "success": true,
  "message": "Posts fetched for #java",
  "data": [ /* same shape as searchPosts */ ],
  "timestamp": "2026-04-21T09:00:00"
}
```

**404 — hashtag not found:**
```json
{
  "success": false,
  "message": "Hashtag not found: #unknowntag",
  "timestamp": "2026-04-21T09:00:00"
}
```

---

#### `GET /hashtags/post/{postId}`

Get all hashtags attached to a specific post.

| Path Variable | Type | Description |
|---|---|---|
| `postId` | Integer | ID of the post |

**Example:**
```
GET /api/v1/hashtags/post/42
```
```json
{
  "success": true,
  "message": "Hashtags fetched",
  "data": [
    { "hashtagId": 5,  "tag": "java",   "postCount": 1532, "lastUsedAt": "2026-04-21T08:55:00" },
    { "hashtagId": 1,  "tag": "spring", "postCount": 987,  "lastUsedAt": "2026-04-21T08:50:00" }
  ],
  "timestamp": "2026-04-21T09:00:00"
}
```

**No hashtags:**
```json
{
  "success": true,
  "message": "No hashtags found for this post",
  "data": [],
  "timestamp": "2026-04-21T09:00:00"
}
```

---

#### `GET /hashtags/search`

Autocomplete hashtag search — partial match ordered by `postCount DESC`.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | String | Yes | Partial tag text (with or without `#`) |

**Example:**
```
GET /api/v1/hashtags/search?query=spring
```
```json
{
  "success": true,
  "message": "Hashtag search completed",
  "data": [
    { "hashtagId": 1,  "tag": "spring",          "postCount": 987 },
    { "hashtagId": 12, "tag": "springboot",       "postCount": 743 },
    { "hashtagId": 19, "tag": "springframework",  "postCount": 201 }
  ],
  "timestamp": "2026-04-21T09:00:00"
}
```

---

#### `GET /hashtags/{tag}/count`

Get the total post count for a specific hashtag.

| Path Variable | Type | Description |
|---|---|---|
| `tag` | String | Hashtag text (no `#` prefix needed) |

**Example:**
```
GET /api/v1/hashtags/java/count
```
```json
{
  "success": true,
  "message": "Hashtag count fetched",
  "data": 1532,
  "timestamp": "2026-04-21T09:00:00"
}
```

**Tag not found — returns 0 (not 404):**
```json
{
  "success": true,
  "message": "Hashtag count fetched",
  "data": 0,
  "timestamp": "2026-04-21T09:00:00"
}
```

---

## 6. RabbitMQ Configuration

### search-service (Consumer side)

| Queue | Routing Key | Bound Exchange | Listener |
|---|---|---|---|
| `connectsphere.search.post.created.queue` | `post.created` | `connectsphere.post.exchange` | `handlePostCreated()` |
| `connectsphere.search.post.updated.queue` | `post.updated` | `connectsphere.post.exchange` | `handlePostUpdated()` |
| `connectsphere.search.post.deleted.queue` | `post.deleted` | `connectsphere.post.exchange` | `handlePostDeleted()` |

### post-service (Publisher side)

| Event | Routing Key | Trigger |
|---|---|---|
| `POST_CREATED` | `post.created` | After `postRepository.save()` in `createPost()` |
| `POST_UPDATED` | `post.updated` | After `postRepository.save()` in `updatePost()` |
| `POST_UPDATED` | `post.updated` | After `postRepository.updateVisibility()` in `changeVisibility()` |
| `POST_DELETED` | `post.deleted` | After `postRepository.softDeleteByPostId()` in `deletePost()` |

### IndexPostEventMessage fields

| Field | Type | Notes |
|---|---|---|
| `eventType` | String | `POST_CREATED` / `POST_UPDATED` / `POST_DELETED` |
| `postId` | Integer | ID of the post |
| `authorId` | Integer | ID of the post author |
| `content` | String | Full post text — hashtags parsed from this |
| `visibility` | String | `PUBLIC` / `FOLLOWERS_ONLY` / `PRIVATE` |
| `previousContent` | String | Old content — only set on `POST_UPDATED` for tag diffing |

---

## 7. Feign Clients

### PostServiceClient → `post-service`

| Method | Maps To | Used For |
|---|---|---|
| `getPostById(postId)` | `GET /posts/{postId}` | Enrich a single postId with full post details |
| `searchPosts(keyword)` | `GET /posts/search?keyword=` | Full-text keyword search |

### AuthServiceClient → `auth-service`

| Method | Maps To | Used For |
|---|---|---|
| `searchUsers(query)` | `GET /auth/search?query=` | User search by username/fullName |

Both clients are resolved via Eureka (`lb://post-service`, `lb://auth-service`).
The `FeignClientConfig` interceptor forwards the incoming `Authorization: Bearer <jwt>` header to all outgoing Feign calls.
For unauthenticated (guest) requests, no header is forwarded — both target endpoints have public GET access.

---

## 8. Service Classes

### SearchServiceImpl — method summary

| Method | Behaviour |
|---|---|
| `indexPost()` | Skip if non-PUBLIC or empty content. Parse tags. Upsert each Hashtag. Create PostHashtag mapping (dedup guarded). |
| `reIndexPost()` | If visibility changed to non-PUBLIC → `removePostIndex()`. Otherwise diff old/new tags: remove stale, add new. |
| `removePostIndex()` | Load all PostHashtag rows for postId. Decrement each Hashtag's postCount atomically. Delete all mappings. |
| `searchPosts()` | `#` prefix → local hashtag index. Plain keyword → Feign call to post-service. Enrich results with hashtag tags. |
| `searchUsers()` | Feign call to auth-service. Filter inactive users. Return empty list on failure (graceful degradation). |
| `getHashtagsForPost()` | Load hashtagIds from PostHashtag table. Fetch each Hashtag by ID. Return DTO list. |
| `getTrendingHashtags()` | Cap limit at 50. Query `findTrendingHashtags(pageable)` ordered by `postCount DESC`. |
| `getPostsByHashtag()` | Throw `HashtagNotFoundException` if tag missing. Then `searchByHashtagInternal()`. |
| `searchHashtags()` | `LIKE %query%` on tag column, ordered by `postCount DESC`. Strips `#` from query if present. |
| `getHashtagCount()` | Returns `postCount` from Hashtag record. Returns `0` if tag not found. |

### HashtagRepository — key queries

| Method | Purpose |
|---|---|
| `findByTag(tag)` | Exact lookup for upsert logic |
| `findTrendingHashtags(pageable)` | `ORDER BY post_count DESC` — drives trending |
| `incrementPostCount(hashtagId)` | Atomic `UPDATE SET post_count = post_count + 1` |
| `decrementPostCount(hashtagId)` | Atomic `UPDATE SET post_count = GREATEST(0, post_count - 1)` |
| `findByTagContainingIgnoreCaseOrderByPostCountDesc(tag)` | Autocomplete |
| `findPostCountByTag(tag)` | Count query for `getHashtagCount()` |

### PostHashtagRepository — key queries

| Method | Purpose |
|---|---|
| `findPostIdsByHashtagTag(tag)` | Hashtag feed — all postIds for a tag |
| `findHashtagIdsByPostId(postId)` | Tags on a specific post |
| `findByPostId(postId)` | Full PostHashtag rows — for decrement on delete |
| `existsByPostIdAndHashtagId(postId, hashtagId)` | Deduplication guard |
| `deleteByPostId(postId)` | Remove all mappings on post deletion |

---

## 9. post-service Changes

Four files added and three files modified to wire up the publisher side.

### New files

**`messaging/IndexPostEventMessage.java`**
RabbitMQ message payload. Fields: `eventType`, `postId`, `authorId`, `content`, `visibility`, `previousContent`.

**`messaging/PostIndexPublisher.java`**
Three `@Async` methods:
- `publishPostCreated(postId, authorId, content, visibility)`
- `publishPostUpdated(postId, authorId, newContent, previousContent, visibility)`
- `publishPostDeleted(postId)`

Each wraps `rabbitTemplate.convertAndSend()` in try-catch — failure logs a warning but never throws. The post save is never rolled back due to a messaging failure.

**`config/SearchRabbitMQConfig.java`**
Declares `connectsphere.post.exchange` (DirectExchange, durable). Configures `Jackson2JsonMessageConverter` and `RabbitTemplate`.

### Modified files

**`PostServiceApplication.java`** — added `@EnableAsync`

**`pom.xml`** — added `spring-boot-starter-amqp`

**`application.yml`** — added `spring.rabbitmq` connection config and `search.rabbitmq` routing key properties

**`PostServiceImpl.java`** — four publish calls added:

| Method | Publisher call added |
|---|---|
| `createPost()` | `publishPostCreated()` after `postRepository.save()` |
| `updatePost()` | `publishPostUpdated()` after `postRepository.save()` — captures `previousContent` before update |
| `deletePost()` | `publishPostDeleted()` after `postRepository.softDeleteByPostId()` |
| `changeVisibility()` | `publishPostUpdated()` after `postRepository.updateVisibility()` — content same, visibility changes |

---

## 10. Security

All `search-service` endpoints are public — no JWT required. This matches case study section 2.2:
> *"Guests can browse public posts, view public profiles, and search users or posts without logging in."*

`JwtAuthenticationFilter` is still present and active. If a valid JWT is included, the user's `userId` and `role` are set in `SecurityContext` and request attributes. This allows future admin-only endpoints (e.g. manually triggering a re-index) to be added with `@PreAuthorize("hasRole('ADMIN')")` without any config changes.

---

## 11. Non-Functional Notes

| Concern | Behaviour |
|---|---|
| **post-service down** | Feign enrichment returns `null` per post — those posts are filtered out. Empty list returned. No exception propagated. |
| **auth-service down** | `searchUsers()` catches the Feign exception and returns empty list with `success: true`. |
| **RabbitMQ down** | Post saves succeed. `@Async` publisher catches the exception and logs a warning. Hashtag index is temporarily stale — self-heals when RabbitMQ comes back. |
| **Non-PUBLIC posts** | Skipped in `indexPost()`. De-indexed in `reIndexPost()` if visibility changes away from PUBLIC. Never appear in any search result. |
| **Duplicate mappings** | `existsByPostIdAndHashtagId()` guard prevents re-inserting the same PostHashtag row. |
| **postCount floor** | `GREATEST(0, post_count - 1)` ensures postCount never goes negative even if events arrive out of order. |
| **Trending limit cap** | `Math.min(limit, 50)` — prevents unbounded DB reads. |
| **Tag normalisation** | Tags are lowercased and `#` stripped before storage. `#Spring`, `#spring`, `#SPRING` all map to the same `Hashtag` record. |
| **Long tags** | Tags longer than 100 chars are silently dropped during `extractHashtags()`. |
| **Performance** | `postCount` is denormalised — trending query is a simple `ORDER BY` with no aggregation. Suitable for 50k concurrent users per NFR spec. |
