package com.connectsphere.comment.service;

import com.connectsphere.comment.client.PostClient;
import com.connectsphere.comment.dto.*;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.CommentNotFoundException;
import com.connectsphere.comment.exception.PostNotFoundException;
import com.connectsphere.comment.exception.PostServiceUnavailableException;
import com.connectsphere.comment.exception.UnauthorizedActionException;
import com.connectsphere.comment.repository.CommentRepository;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CommentServiceImpl - Business Logic Implementation
 * Key flows:
 * 1. addComment()    -> verifyPostExists() -> Validate parentCommentId (if reply)
 *                    -> Save Comment -> PostClient.incrementCommentCount()
 * 2. deleteComment() -> Ownership check -> softDeleteByCommentId()
 *                    -> PostClient.decrementCommentCount()
 * 3. getCommentsByPost() -> Returns all comments (incl. soft-deleted with placeholder)
 * 4. likeComment()   -> Atomic incrementLikes() — no full entity load
 *
 * Inter-service calls (via FeignClient — replaces old RestTemplate):
 *   POST-SERVICE: GET  /posts/{postId}                    → verify post exists
 *   POST-SERVICE: POST /posts/{postId}/comments/increment → after comment added
 *   POST-SERVICE: POST /posts/{postId}/comments/decrement → after comment deleted
 *
 * authorId is NEVER taken from request body — always from JWT token.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostClient postClient;  // FeignClient

    // ── ADD COMMENT ──

    @Override
    @Transactional
    public ApiResponseDTO<CommentResponseDTO> addComment(Integer authorId,
                                                         AddCommentRequestDTO request) {
        log.info("Adding comment for postId: {} by authorId: {}",
                request.getPostId(), authorId);

        // STEP 1 — Verify post exists in post-service before allowing comment
        verifyPostExists(request.getPostId());

        // STEP 2 — If this is a reply, validate parent comment exists and is not deleted
        if (request.getParentCommentId() != null) {
            commentRepository.findByCommentIdAndIsDeletedFalse(request.getParentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            "Parent comment not found with id: " +
                                    request.getParentCommentId()));
        }

        // STEP 3 — Build and save comment
        Comment comment = Comment.builder()
                .postId(request.getPostId())
                .authorId(authorId)
                .parentCommentId(request.getParentCommentId())
                .content(request.getContent())
                .likesCount(0)
                .isDeleted(false)
                .build();

        Comment saved = commentRepository.save(comment);
        log.info("Comment saved with commentId: {}", saved.getCommentId());

        // STEP 4 — Notify post-service to increment commentsCount
        notifyPostServiceIncrement(request.getPostId());

        return ApiResponseDTO.success("Comment added successfully", toDTO(saved));
    }

    // ── READ OPERATIONS ────

    @Override
    public ApiResponseDTO<List<CommentResponseDTO>> getCommentsByPost(Integer postId) {
        log.debug("Fetching all comments for postId: {}", postId);
        List<CommentResponseDTO> comments = commentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toDTO)   // toDTO handles placeholder for deleted comments
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Comments fetched successfully", comments);
    }

    @Override
    public ApiResponseDTO<List<CommentResponseDTO>> getTopLevelComments(Integer postId) {
        log.debug("Fetching top-level comments for postId: {}", postId);
        List<CommentResponseDTO> comments = commentRepository
                .findTopLevelByPostId(postId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Top-level comments fetched successfully", comments);
    }

    @Override
    public ApiResponseDTO<CommentResponseDTO> getCommentById(Integer commentId) {
        Comment comment = commentRepository.findByCommentId(commentId)
                .orElseThrow(() -> new CommentNotFoundException(
                        "Comment not found with id: " + commentId));
        return ApiResponseDTO.success("Comment fetched successfully", toDTO(comment));
    }

    @Override
    public ApiResponseDTO<List<CommentResponseDTO>> getReplies(Integer parentCommentId) {
        log.debug("Fetching replies for parentCommentId: {}", parentCommentId);
        List<CommentResponseDTO> replies = commentRepository
                .findByParentCommentIdOrderByCreatedAtAsc(parentCommentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("Replies fetched successfully", replies);
    }

    @Override
    public ApiResponseDTO<List<CommentResponseDTO>> getCommentsByUser(Integer authorId) {
        log.debug("Fetching comments by authorId: {}", authorId);
        List<CommentResponseDTO> comments = commentRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ApiResponseDTO.success("User comments fetched successfully", comments);
    }

    // ── UPDATE COMMENT ───

    @Override
    @Transactional
    public ApiResponseDTO<CommentResponseDTO> updateComment(Integer commentId,
                                                            Integer requestingUserId,
                                                            UpdateCommentRequestDTO request) {
        log.info("Update request for commentId: {} by userId: {}", commentId, requestingUserId);

        Comment comment = findActiveCommentById(commentId);
        checkOwnership(comment, requestingUserId);

        comment.setContent(request.getContent());
        Comment updated = commentRepository.save(comment);

        log.info("Comment updated: {}", commentId);
        return ApiResponseDTO.success("Comment updated successfully", toDTO(updated));
    }

    // ── DELETE COMMENT ───

    @Override
    @Transactional
    public ApiResponseDTO<String> deleteComment(Integer commentId,
                                                Integer requestingUserId,
                                                String requestingUserRole) {
        log.info("Delete request for commentId: {} by userId: {}", commentId, requestingUserId);

        Comment comment = findActiveCommentById(commentId);

        boolean isAdminOrModerator = "ADMIN".equalsIgnoreCase(requestingUserRole)
                || "MODERATOR".equalsIgnoreCase(requestingUserRole);

        if (!isAdminOrModerator) {
            checkOwnership(comment, requestingUserId);
        }

        commentRepository.softDeleteByCommentId(commentId);
        log.info("Comment soft-deleted: {}", commentId);

        // Notify post-service to decrement commentsCount
        notifyPostServiceDecrement(comment.getPostId());

        return ApiResponseDTO.success("Comment deleted successfully");
    }

    // ── LIKE / UNLIKE ──

    @Override
    @Transactional
    public ApiResponseDTO<String> likeComment(Integer commentId) {
        ensureCommentExists(commentId);
        commentRepository.incrementLikes(commentId);
        log.debug("Likes incremented for commentId: {}", commentId);
        return ApiResponseDTO.success("Comment liked successfully");
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> unlikeComment(Integer commentId) {
        ensureCommentExists(commentId);
        commentRepository.decrementLikes(commentId);
        log.debug("Likes decremented for commentId: {}", commentId);
        return ApiResponseDTO.success("Comment unliked successfully");
    }

    // ── COMMENT COUNT ───

    @Override
    public ApiResponseDTO<Integer> getCommentCount(Integer postId) {
        int count = commentRepository.countByPostIdAndIsDeletedFalse(postId);
        return ApiResponseDTO.success("Comment count fetched", count);
    }

    // ── PRIVATE HELPERS ───

    /**
     * Verify post exists via FeignClient call to post-service.
     *
     * Called BEFORE saving a comment — blocks orphan comment creation.
     *
     * Two failure scenarios handled:
     *   FeignException.NotFound (404) → post does not exist
     *                                 → throws PostNotFoundException (404)
     *   Any other exception           → post-service is down
     *                                 → throws PostServiceUnavailableException (503)
     *
     * Also blocks comments on soft-deleted posts (isDeleted = true).
     */
    private void verifyPostExists(Integer postId) {
        try {
            log.debug("Verifying post exists: postId={}", postId);

            PostApiResponse response = postClient.getPostById(postId);

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new PostNotFoundException("Post not found with id: " + postId);
            }

            if (Boolean.TRUE.equals(response.getData().getIsDeleted())) {
                throw new PostNotFoundException(
                        "Cannot comment on a deleted post. postId: " + postId);
            }

            log.debug("Post verified successfully: postId={}", postId);

        } catch (PostNotFoundException e) {
            throw e;

        } catch (FeignException.NotFound e) {
            log.warn("Post not found in post-service: postId={}", postId);
            throw new PostNotFoundException("Post not found with id: " + postId);

        } catch (FeignException e) {
            log.error("Feign error for postId={}: status={}", postId, e.status());
            if (e.status() == 404) {
                throw new PostNotFoundException("Post not found with id: " + postId);
            }
            throw new PostServiceUnavailableException(
                    "Post service error. Please try again later.");

        } catch (Exception e) {
            log.error("Post-service unreachable for postId={}: {}", postId, e.getMessage());
            throw new PostServiceUnavailableException(
                    "Post service is currently unavailable. Please try again later.");
        }
    }

    /**
     * Notify post-service to increment commentsCount after addComment().
     *
     * Fire-and-forget — post existence already verified before this call.
     * If this fails, comment is still saved. Count is eventually consistent.
     */
    private void notifyPostServiceIncrement(Integer postId) {
        try {
            postClient.incrementCommentCount(postId);
            log.debug("Post-service notified: increment commentsCount for postId={}", postId);
        } catch (Exception e) {
            log.warn("Failed to increment commentsCount on post-service (postId={}): {}",
                    postId, e.getMessage());
        }
    }

    /**
     * Notify post-service to decrement commentsCount after deleteComment().
     *
     * Fire-and-forget — graceful degradation on failure.
     */
    private void notifyPostServiceDecrement(Integer postId) {
        try {
            postClient.decrementCommentCount(postId);
            log.debug("Post-service notified: decrement commentsCount for postId={}", postId);
        } catch (Exception e) {
            log.warn("Failed to decrement commentsCount on post-service (postId={}): {}",
                    postId, e.getMessage());
        }
    }

    /**
     * Find an active (non-deleted) comment — throws CommentNotFoundException if not found.
     */
    private Comment findActiveCommentById(Integer commentId) {
        return commentRepository.findByCommentIdAndIsDeletedFalse(commentId)
                .orElseThrow(() -> new CommentNotFoundException(
                        "Comment not found with id: " + commentId));
    }

    /**
     * Check that the requesting user is the owner of the comment.
     * Throws UnauthorizedActionException if not the owner.
     */
    private void checkOwnership(Comment comment, Integer requestingUserId) {
        if (!comment.getAuthorId().equals(requestingUserId)) {
            throw new UnauthorizedActionException(
                    "You are not authorized to modify this comment");
        }
    }

    /**
     * Verify comment exists and is active for like/unlike operations.
     * Throws CommentNotFoundException if not found or already deleted.
     */
    private void ensureCommentExists(Integer commentId) {
        if (commentRepository.findByCommentIdAndIsDeletedFalse(commentId).isEmpty()) {
            throw new CommentNotFoundException("Comment not found with id: " + commentId);
        }
    }

    /**
     * Map Comment entity → CommentResponseDTO.
     *
     * KEY RULE: If comment is soft-deleted, replace content with placeholder.
     * This preserves thread structure while hiding the deleted text.
     */
    private CommentResponseDTO toDTO(Comment comment) {
        String content = Boolean.TRUE.equals(comment.getIsDeleted())
                ? "[This comment was deleted]"
                : comment.getContent();

        return CommentResponseDTO.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentCommentId(comment.getParentCommentId())
                .content(content)
                .likesCount(comment.getLikesCount())
                .isDeleted(comment.getIsDeleted())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}