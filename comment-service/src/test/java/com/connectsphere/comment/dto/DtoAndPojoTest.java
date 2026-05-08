package com.connectsphere.comment.dto;

import com.connectsphere.comment.messaging.NotificationEventMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DtoAndPojoTest {

    @Test
    void testCommentResponseDTO() {
        CommentResponseDTO dto = new CommentResponseDTO();
        dto.setCommentId(1);
        dto.setContent("Hi");
        dto.setLikesCount(10);
        assertEquals(1, dto.getCommentId());
        assertEquals("Hi", dto.getContent());
        assertEquals(10, dto.getLikesCount());
    }

    @Test
    void testPostResponseDTO() {
        PostResponseDTO dto = new PostResponseDTO();
        dto.setPostId(1);
        dto.setContent("Post");
        dto.setLikesCount(5);
        assertEquals(1, dto.getPostId());
        assertEquals(5, dto.getLikesCount());
    }

    @Test
    void testAddCommentRequestDTO() {
        AddCommentRequestDTO dto = new AddCommentRequestDTO(1, null, "Content");
        dto.setParentCommentId(2);
        assertEquals(1, dto.getPostId());
        assertEquals(2, dto.getParentCommentId());
        assertEquals("Content", dto.getContent());
    }

    @Test
    void testUpdateCommentRequestDTO() {
        UpdateCommentRequestDTO dto = new UpdateCommentRequestDTO("New");
        dto.setContent("Updated");
        assertEquals("Updated", dto.getContent());
    }

    @Test
    void testPostApiResponse() {
        PostApiResponse resp = new PostApiResponse(true, "Ok", null);
        resp.setMessage("Success");
        assertTrue(resp.isSuccess());
        assertEquals("Success", resp.getMessage());
    }

    @Test
    void testNotificationEventMessage() {
        NotificationEventMessage msg = new NotificationEventMessage(1, 2, "LIKE", "Msg", 1, "POST", "url");
        msg.setType("COMMENT");
        assertEquals("COMMENT", msg.getType());
        assertEquals(1, msg.getRecipientId());
    }
}
