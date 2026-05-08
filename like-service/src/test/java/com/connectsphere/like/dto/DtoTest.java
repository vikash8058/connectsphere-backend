package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.messaging.NotificationEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testLikeResponseDTO() {
        LikeResponseDTO dto = new LikeResponseDTO();
        dto.setLikeId(1);
        dto.setUserId(2);
        dto.setTargetId(3);
        dto.setTargetType(TargetType.POST);
        dto.setReactionType(ReactionType.LOVE);
        assertEquals(1, dto.getLikeId());
        assertEquals(ReactionType.LOVE, dto.getReactionType());
    }

    @Test
    void testLikeRequestDTO() {
        LikeRequestDTO dto = new LikeRequestDTO();
        dto.setTargetId(1);
        dto.setTargetType(TargetType.COMMENT);
        dto.setReactionType(ReactionType.HAHA);
        assertEquals(1, dto.getTargetId());
        assertEquals(ReactionType.HAHA, dto.getReactionType());
    }

    @Test
    void testChangeReactionRequestDTO() {
        ChangeReactionRequestDTO dto = new ChangeReactionRequestDTO(1, TargetType.POST, ReactionType.WOW);
        assertEquals(1, dto.getTargetId());
        assertEquals(ReactionType.WOW, dto.getNewReactionType());
    }

    @Test
    void testReactionSummaryDTO() {
        ReactionSummaryDTO dto = new ReactionSummaryDTO();
        dto.setTargetId(1);
        dto.setTotalCount(10);
        assertEquals(1, dto.getTargetId());
        assertEquals(10, dto.getTotalCount());
    }

    @Test
    void testNotificationEventMessage() {
        NotificationEventMessage msg = new NotificationEventMessage(1, 2, "LIKE", "msg", 3, "POST", "/url");
        assertEquals(1, msg.getRecipientId());
        assertEquals("LIKE", msg.getType());
    }

    @Test
    void testPostApiResponse() {
        PostApiResponse.PostData data = new PostApiResponse.PostData(1, 2);
        PostApiResponse resp = new PostApiResponse(true, data);
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().getPostId());
        assertEquals(2, resp.getData().getAuthorId());
    }
}
