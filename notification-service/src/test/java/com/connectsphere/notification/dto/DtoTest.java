package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.ReportStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void testNotificationResponseDTO() {
        NotificationResponseDTO dto = new NotificationResponseDTO();
        dto.setNotificationId(1);
        dto.setRecipientId(2);
        dto.setActorId(3);
        dto.setType(NotificationType.LIKE);
        dto.setMessage("liked");
        dto.setTargetId(10);
        dto.setTargetType("POST");
        dto.setDeepLinkUrl("/posts/10");
        dto.setIsRead(false);
        dto.setActorUsername("john");
        dto.setActorProfilePic("pic.jpg");
        assertEquals(1, dto.getNotificationId());
        assertEquals("john", dto.getActorUsername());
        assertFalse(dto.getIsRead());
    }

    @Test
    void testNotificationEventMessage() {
        NotificationEventMessage msg = new NotificationEventMessage(1, 2, NotificationType.COMMENT, "msg", 3, "POST", "/url");
        assertEquals(1, msg.getRecipientId());
        assertEquals(NotificationType.COMMENT, msg.getType());
    }

    @Test
    void testCreateReportRequestDTO() {
        CreateReportRequestDTO dto = new CreateReportRequestDTO(1, "POST", "Spam");
        dto.setReason("Updated");
        assertEquals(1, dto.getTargetId());
        assertEquals("Updated", dto.getReason());
    }

    @Test
    void testReportResponseDTO() {
        ReportResponseDTO dto = new ReportResponseDTO();
        dto.setReportId(1);
        dto.setReporterId(2);
        dto.setReporterUsername("user1");
        dto.setTargetId(3);
        dto.setTargetType("POST");
        dto.setReason("Spam");
        dto.setStatus(ReportStatus.PENDING);
        assertEquals(1, dto.getReportId());
        assertEquals("user1", dto.getReporterUsername());
        assertEquals(ReportStatus.PENDING, dto.getStatus());
    }

    @Test
    void testReportStatsDTO() {
        ReportStatsDTO dto = ReportStatsDTO.builder()
                .total(10).pending(3).resolved(5).dismissed(2).build();
        assertEquals(10, dto.getTotal());
        assertEquals(3, dto.getPending());
    }

    @Test
    void testUserDataDTO() {
        UserDataDTO.UserDTO user = new UserDataDTO.UserDTO(1, "john", "John Doe", "j@b.com", "pic.jpg");
        UserDataDTO dto = new UserDataDTO(true, user);
        assertTrue(dto.isSuccess());
        assertEquals("john", dto.getData().getUsername());
        assertEquals("John Doe", dto.getData().getFullName());
        assertEquals("j@b.com", dto.getData().getEmail());
        assertEquals("pic.jpg", dto.getData().getProfilePicUrl());
    }
}
