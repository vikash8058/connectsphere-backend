package com.connectsphere.follow.dto;

import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.message.NotificationEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testFollowResponseDTO() {
        FollowResponseDTO dto = new FollowResponseDTO();
        dto.setFollowId(1);
        dto.setFollowerId(2);
        dto.setFolloweeId(3);
        dto.setStatus(FollowStatus.ACTIVE);
        assertEquals(1, dto.getFollowId());
        assertEquals(FollowStatus.ACTIVE, dto.getStatus());
    }

    @Test
    void testFollowCountDTO() {
        FollowCountDTO dto = FollowCountDTO.builder()
                .userId(1).followerCount(10).followingCount(5).build();
        assertEquals(1, dto.getUserId());
        assertEquals(10, dto.getFollowerCount());
        assertEquals(5, dto.getFollowingCount());
    }

    @Test
    void testNotificationEventMessage() {
        NotificationEventMessage msg = NotificationEventMessage.builder()
                .recipientId(1).actorId(2).type("FOLLOW")
                .message("followed").targetId(3).targetType("USER").deepLinkUrl("/profile/2").build();
        assertEquals(1, msg.getRecipientId());
        assertEquals("FOLLOW", msg.getType());
        assertEquals("/profile/2", msg.getDeepLinkUrl());
    }

    @Test
    void testUserDataDTO() {
        UserDataDTO dto = new UserDataDTO();
        dto.setUserId(1);
        dto.setIsActive(true);
        assertEquals(1, dto.getUserId());
        assertTrue(dto.getIsActive());
    }

    @Test
    void testUserExistsResponseDTO() {
        UserExistsResponseDTO dto = new UserExistsResponseDTO();
        dto.setSuccess(true);
        dto.setMessage("OK");
        UserDataDTO user = new UserDataDTO();
        user.setUserId(1);
        dto.setData(user);
        assertTrue(dto.isSuccess());
        assertEquals(1, dto.getData().getUserId());
    }

    @Test
    void testApiResponseDTO() {
        ApiResponseDTO<String> dto = ApiResponseDTO.success("OK", "data");
        assertTrue(dto.isSuccess());
        assertEquals("data", dto.getData());

        ApiResponseDTO<String> dto2 = ApiResponseDTO.success("OK");
        assertTrue(dto2.isSuccess());
        assertNull(dto2.getData());

        ApiResponseDTO<String> dto3 = ApiResponseDTO.error("Fail");
        assertFalse(dto3.isSuccess());
    }
}
