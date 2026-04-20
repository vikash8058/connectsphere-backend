# Notification Service — ConnectSphere

Complete documentation for the `notification-service` microservice.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [How It Works — End to End](#3-how-it-works--end-to-end)
4. [What Gets Stored](#4-what-gets-stored)
5. [Email Rules](#5-email-rules)
6. [REST API Endpoints](#6-rest-api-endpoints)
7. [RabbitMQ Configuration](#7-rabbitmq-configuration)
8. [Service Classes](#8-service-classes)
9. [Publisher Services](#9-publisher-services)
10. [Frontend Integration](#10-frontend-integration)
11. [Bug Fixes Applied](#11-bug-fixes-applied)
12. [Non-Functional Notes](#12-non-functional-notes)

---

## 1. Overview

`notification-service` is the single service responsible for storing and serving all in-app notifications on ConnectSphere. It listens to a RabbitMQ queue, saves notification rows to its own MySQL database, and exposes REST endpoints for the frontend to read, mark as read, and delete notifications.

**What it does:**
- Receives events from `like-service`, `comment-service`, and `follow-service` via RabbitMQ
- Saves one DB row per event (in-app notification)
- Serves the unread badge count to the frontend
- Sends email **only** on high-priority events (follower milestones)

**What it does NOT do:**
- Send email on every like, comment, reply, or mention
- Block or slow down the publisher services (fully async)

---

## 2. Architecture

```
like-service      ──┐
comment-service   ──┼──▶  RabbitMQ Queue  ──▶  NotificationListener  ──▶  DB
follow-service    ──┘                               (notification-service)
```

- **Publisher services** call `rabbitTemplate.convertAndSend()` — fire and forget
- **RabbitMQ** queues the message, retries on failure
- **NotificationListener** deserializes JSON and calls `createNotification()`
- **DB** stores one row per notification event
- **Frontend** polls REST endpoints for badge count and notification list

Base package: `com.connectsphere.notification`
Port: configured in `application.yml`
Context path: `/api/v1`

---

## 3. How It Works — End to End

### When a user likes a post

```
1. like-service.likeTarget()
      → saves Like to like_db
      → publishLikeNotification()
            → rabbitTemplate.convertAndSend(exchange, routingKey, message)

2. RabbitMQ delivers message to connectsphere.notification.queue

3. NotificationListener.handleNotificationEvent(message)
      → builds CreateNotificationRequestDTO
      → calls notificationService.createNotification()

4. NotificationServiceImpl.createNotification()
      → self-notification check  (actor == recipient? skip)
      → deduplication check      (same actor+target+type already exists? skip)
      → fetch actor name from auth-service  (e.g. "Rahul Kumar")
      → fetch recipient email from auth-service
      → build message: "Rahul Kumar liked your post"
      → save Notification row to DB
      → isHighPriorityEmailEvent(LIKE) → false → NO email

5. Done. One row in notifications table.
```

### When a user comments on a post

```
1. comment-service.addComment()
      → verifyPostExists() → returns postAuthorId  ← (fixed bug)
      → saves Comment to comment_db
      → publishCommentNotification(actorId, postAuthorId, postId, parentCommentId)
            → type = "COMMENT" if top-level, "REPLY" if parentCommentId != null
            → rabbitTemplate.convertAndSend(...)

2. → RabbitMQ → NotificationListener → createNotification()
      → saves row, type = COMMENT or REPLY
      → isHighPriorityEmailEvent(COMMENT/REPLY) → false → NO email
```

### When a user follows another user

```
1. follow-service.follow()
      → saves Follow to follow_db
      → publishFollowNotification(followerId, followeeId)
            → rabbitTemplate.convertAndSend(...)

2. → RabbitMQ → NotificationListener → createNotification()
      → saves row, type = FOLLOW
      → isHighPriorityEmailEvent(FOLLOW)
            → isFollowerMilestone(recipientId)
                  → countByRecipientIdAndType(recipientId, FOLLOW)
                  → count == 100 or 500 or 1000 or 5000 or 10000?
                        YES → sendEmailAlertAsync()  "🎉 Milestone reached!"
                        NO  → no email
```

---

## 4. What Gets Stored

Every notification is one row in the `notifications` table:

| Column | Type | Description |
|---|---|---|
| `notification_id` | INT PK | Auto-generated |
| `recipient_id` | INT | User who receives the notification |
| `actor_id` | INT | User who triggered the event |
| `type` | ENUM | `LIKE`, `COMMENT`, `REPLY`, `FOLLOW`, `MENTION` |
| `message` | VARCHAR | Human-readable e.g. "Rahul Kumar liked your post" |
| `target_id` | INT | The postId or commentId the event is about |
| `target_type` | VARCHAR | `POST` or `COMMENT` |
| `deep_link_url` | VARCHAR | e.g. `/posts/42` — where to navigate on click |
| `is_read` | BOOLEAN | `false` when created, `true` after user reads |
| `created_at` | DATETIME | Auto-set on insert |

---

## 5. Email Rules

Per case study spec (section 2.5):
> *"Email alerts are sent for high-priority events (e.g. account actions, new follower milestones)."*

| Notification Type | Email Sent? | Reason |
|---|---|---|
| `LIKE` | ❌ Never | High-volume — 1000 likes = 1000 emails = inbox spam |
| `COMMENT` | ❌ Never | High-volume social interaction |
| `REPLY` | ❌ Never | High-volume social interaction |
| `MENTION` | ❌ Never | High-volume social interaction |
| `FOLLOW` (regular) | ❌ No | In-app only |
| `FOLLOW` (milestone) | ✅ Yes | Only at 100, 500, 1000, 5000, 10000 followers |
| Admin broadcast | ✅ Yes | Via `/notifications/bulk` endpoint explicitly |
| Account actions | ✅ Yes | Via `/notifications/email-alert` endpoint explicitly |

### Milestone thresholds

```java
return followerCount == 100  ||
       followerCount == 500  ||
       followerCount == 1000 ||
       followerCount == 5000 ||
       followerCount == 10000;
```

Milestone email subject: `"🎉 Milestone reached! You have a new follower — ConnectSphere"`

---

## 6. REST API Endpoints

Base path: `GET /api/v1/notifications/...`

### User endpoints (JWT required)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/recipient/{userId}` | Get all notifications for a user |
| `GET` | `/recipient/{userId}?isRead=false` | Get only unread notifications |
| `GET` | `/recipient/{userId}/unread-count` | Get unread count for badge |
| `POST` | `/{notificationId}/read` | Mark one notification as read |
| `POST` | `/recipient/{userId}/read-all` | Mark all notifications as read |
| `DELETE` | `/{notificationId}` | Delete one notification |

### Admin endpoints (ROLE_ADMIN required)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/bulk` | Broadcast notification to multiple users |
| `POST` | `/email-alert` | Send a direct email alert |
| `GET` | `/all` | Get all platform notifications |
| `GET` | `/type/{type}` | Filter by type (LIKE, COMMENT, etc.) |

### Internal endpoint (service-to-service, no JWT)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/internal` | Create notification directly (REST fallback if RabbitMQ down) |

### Example responses

**GET `/recipient/5/unread-count`**
```json
{
  "success": true,
  "message": "Unread count fetched",
  "data": 7
}
```

**GET `/recipient/5?isRead=false`**
```json
{
  "success": true,
  "message": "Notifications fetched successfully",
  "data": [
    {
      "notificationId": 101,
      "recipientId": 5,
      "actorId": 12,
      "type": "LIKE",
      "message": "Rahul Kumar liked your post",
      "targetId": 42,
      "targetType": "POST",
      "deepLinkUrl": "/posts/42",
      "isRead": false,
      "createdAt": "2026-04-20T10:30:00"
    }
  ]
}
```

---

## 7. RabbitMQ Configuration

### application.yml properties

```yaml
notification:
  rabbitmq:
    exchange: connectsphere.notification.exchange
    queue: connectsphere.notification.queue
    routing-key: notification.event
```

### RabbitMQConfig

```
Exchange  →  connectsphere.notification.exchange  (DirectExchange)
Queue     →  connectsphere.notification.queue
Binding   →  exchange + routing-key → queue
Converter →  Jackson2JsonMessageConverter  (JSON serialization)
```

### Message format (NotificationEventMessage)

```json
{
  "recipientId": 5,
  "actorId": 12,
  "type": "LIKE",
  "message": "Someone liked your post",
  "targetId": 42,
  "targetType": "POST",
  "deepLinkUrl": "/posts/42"
}
```

---

## 8. Service Classes

### NotificationServiceImpl — key methods

| Method | What it does |
|---|---|
| `createNotification(request)` | Self-check → dedupe → build message → save → email guard |
| `sendBulkNotification(request)` | Admin broadcast — saves N rows for N recipients |
| `markAsRead(notificationId, userId)` | Ownership check → atomic UPDATE (no full entity load) |
| `markAllRead(recipientId)` | Bulk UPDATE — all unread → read for one user |
| `getByRecipient(recipientId, isRead)` | Fetch list, optionally filtered by read state |
| `getUnreadCount(recipientId)` | COUNT query → badge number |
| `deleteNotification(id, userId, role)` | Owner or ADMIN can delete |
| `sendEmailAlert(request)` | Admin-triggered email, runs `@Async` |
| `isHighPriorityEmailEvent(type, recipientId)` | Returns true only for follower milestones |
| `isFollowerMilestone(recipientId)` | Counts FOLLOW notifications — checks milestone thresholds |

### NotificationRepository — key queries

| Method | Purpose |
|---|---|
| `findByRecipientIdOrderByCreatedAtDesc` | Full notification feed |
| `findByRecipientIdAndIsReadOrderByCreatedAtDesc` | Filtered by read state |
| `countByRecipientIdAndIsRead` | Unread badge count |
| `findByActorIdAndTargetIdAndType` | Deduplication check |
| `markAsReadById` | Atomic single UPDATE |
| `markAllAsReadByRecipient` | Atomic bulk UPDATE |
| `countByRecipientIdAndType` | Follower milestone count |

---

## 9. Publisher Services

### like-service — publishLikeNotification()

```java
// Called after likeTarget() saves successfully
// Resolves recipientId by fetching post author from post-service via Feign
NotificationEventMessage message = NotificationEventMessage.builder()
    .recipientId(post.getAuthorId())
    .actorId(userId)
    .type("LIKE")
    .message("Someone liked your post")
    .targetId(postId)
    .targetType("POST")
    .deepLinkUrl("/posts/" + postId)
    .build();
rabbitTemplate.convertAndSend(exchange, routingKey, message);
```

### comment-service — publishCommentNotification()

```java
// type = "COMMENT" for top-level, "REPLY" if parentCommentId != null
// recipientId = postAuthorId returned by verifyPostExists()
NotificationEventMessage event = NotificationEventMessage.builder()
    .recipientId(postAuthorId)   // ← correctly returned from verifyPostExists()
    .actorId(authorId)
    .type(parentCommentId != null ? "REPLY" : "COMMENT")
    .message(parentCommentId != null
        ? "Someone replied to your comment"
        : "Someone commented on your post")
    .targetId(postId)
    .targetType("POST")
    .deepLinkUrl("/posts/" + postId)
    .build();
rabbitTemplate.convertAndSend(exchange, routingKey, event);
```

### follow-service — publishFollowNotification()

```java
// recipientId = followeeId (person who got followed)
// actorId     = followerId (person who followed)
NotificationEventMessage message = NotificationEventMessage.builder()
    .recipientId(followeeId)
    .actorId(followerId)
    .type("FOLLOW")
    .message("Someone started following you")
    .deepLinkUrl("/profile/" + followerId)
    .build();
rabbitTemplate.convertAndSend(exchange, routingKey, message);
```

---

## 10. Frontend Integration

### Step 1 — Poll badge count every 30 seconds

```javascript
// Run after user logs in
setInterval(async () => {
  const res = await fetch(`/api/v1/notifications/recipient/${userId}/unread-count`, {
    headers: { Authorization: `Bearer ${jwt}` }
  });
  const { data } = await res.json();  // data = integer e.g. 5
  updateBadge(data);                  // show red circle on bell icon
}, 30_000);
```

### Step 2 — Open bell dropdown

```javascript
async function openNotificationDropdown() {
  const res = await fetch(`/api/v1/notifications/recipient/${userId}?isRead=false`, {
    headers: { Authorization: `Bearer ${jwt}` }
  });
  const { data } = await res.json();
  renderDropdown(data);  // render list with message, time, deep link
}
```

### Step 3 — User clicks a notification

```javascript
async function onNotificationClick(notificationId, deepLinkUrl) {
  await fetch(`/api/v1/notifications/${notificationId}/read`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${jwt}` }
  });
  window.location.href = deepLinkUrl;  // navigate to post/profile
}
```

### Step 4 — Mark all read

```javascript
async function markAllRead() {
  await fetch(`/api/v1/notifications/recipient/${userId}/read-all`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${jwt}` }
  });
  updateBadge(0);
}
```

### Badge update helper

```javascript
function updateBadge(count) {
  const badge = document.getElementById('notif-badge');
  if (count > 0) {
    badge.textContent = count > 99 ? '99+' : count;
    badge.style.display = 'block';
  } else {
    badge.style.display = 'none';
  }
}
```

---

## 11. Bug Fixes Applied

### Bug 1 — Email sent on every notification (notification-service)

**Problem:** `createNotification()` called `sendEmailAlertAsync()` unconditionally.
1000 likes = 1000 emails to the post author's inbox.

**Fix:** Replaced unconditional call with `isHighPriorityEmailEvent()` guard.
LIKE / COMMENT / REPLY / MENTION always return `false` — no email ever.
FOLLOW returns `true` only on milestone follower counts.

```java
// BEFORE (wrong)
if (recipientEmail != null) {
    sendEmailAlertAsync(recipientEmail, request.getType(), finalMessage);
}

// AFTER (correct)
if (recipientEmail != null && isHighPriorityEmailEvent(request.getType(), request.getRecipientId())) {
    sendEmailAlertAsync(recipientEmail, request.getType(), finalMessage);
}
```

### Bug 2 — Comment notifications silently skipped (comment-service)

**Problem:** `verifyPostExists()` fetched the post's `authorId` from Feign response
but then `return null` at the bottom of the method.
So `publishCommentNotification()` always received `recipientId = null`
and the notification was never published to RabbitMQ.

**Fix:** Return `response.getData().getAuthorId()` inside the try block
and remove the orphaned `return null`.

```java
// BEFORE (wrong)
log.debug("Post verified successfully: postId={}", postId);
// ... catch blocks ...
return null;  // ← always returned null

// AFTER (correct)
log.debug("Post verified successfully: postId={}", postId);
return response.getData().getAuthorId();  // ← returns actual postAuthorId
// ... catch blocks (no return null at bottom)
```

---

## 12. Non-Functional Notes

| Concern | Behaviour |
|---|---|
| **RabbitMQ down** | Messages queue up and deliver when it recovers. REST fallback at `/internal` available. |
| **auth-service down** | Actor name defaults to `"Someone"`, email skipped. Notification still saved. |
| **Duplicate events** | Deduplicated by `actorId + targetId + type` check before insert. |
| **Self-notifications** | Skipped if `actorId == recipientId`. |
| **Async email** | `@Async` — never blocks the API response. Failures logged, not thrown. |
| **Atomic mark-read** | Uses `@Modifying @Query` UPDATE — no full entity load on every click. |
| **Unread count** | Simple `COUNT` query — fast even at scale. |
| **Soft delete** | Notifications are hard-deleted (user action). Posts/comments are soft-deleted — media retained 30 days per spec. |