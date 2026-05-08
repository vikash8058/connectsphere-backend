package com.connectsphere.notification.controller;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationResource Controller Tests")
class NotificationResourceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private NotificationService notificationService;
    @InjectMocks private NotificationResource notificationResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationResource).build();
    }

    @Test
    void getByRecipient() throws Exception {
        when(notificationService.getByRecipient(1, null))
                .thenReturn(ApiResponseDTO.<List<NotificationResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/notifications/recipient/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getByRecipient_withIsReadFilter() throws Exception {
        when(notificationService.getByRecipient(1, true))
                .thenReturn(ApiResponseDTO.<List<NotificationResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/notifications/recipient/1").param("isRead", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void getUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(1))
                .thenReturn(ApiResponseDTO.<Integer>builder().success(true).data(5).build());
        mockMvc.perform(get("/notifications/recipient/1/unread-count"))
                .andExpect(status().isOk());
    }

    @Test
    void markAsRead() throws Exception {
        when(notificationService.markAsRead(anyInt(), anyInt()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/notifications/1/read")
                        .requestAttr("requestingUserId", 1))
                .andExpect(status().isOk());
    }

    @Test
    void markAllRead() throws Exception {
        when(notificationService.markAllRead(1))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/notifications/recipient/1/read-all"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteNotification() throws Exception {
        when(notificationService.deleteNotification(anyInt(), anyInt(), anyString()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(delete("/notifications/1")
                        .requestAttr("requestingUserId", 1)
                        .requestAttr("requestingUserRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void createNotification() throws Exception {
        CreateNotificationRequestDTO req = CreateNotificationRequestDTO.builder()
                .recipientId(2).actorId(1).type(NotificationType.LIKE).message("liked").build();
        when(notificationService.createNotification(any()))
                .thenReturn(ApiResponseDTO.<NotificationResponseDTO>builder().success(true).build());
        mockMvc.perform(post("/notifications/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void sendBulkNotification() throws Exception {
        BulkNotificationRequestDTO req = BulkNotificationRequestDTO.builder()
                .recipientIds(List.of(1, 2)).message("Test").type("SYSTEM").build();
        when(notificationService.sendBulkNotification(any()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void sendEmailAlert() throws Exception {
        EmailAlertRequestDTO req = EmailAlertRequestDTO.builder()
                .toEmail("a@b.com").subject("Sub").body("Body").build();
        when(notificationService.sendEmailAlert(any()))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(post("/notifications/email-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void getAll() throws Exception {
        when(notificationService.getAll())
                .thenReturn(ApiResponseDTO.<List<NotificationResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/notifications/all"))
                .andExpect(status().isOk());
    }

    @Test
    void getByType() throws Exception {
        when(notificationService.getByType(NotificationType.LIKE))
                .thenReturn(ApiResponseDTO.<List<NotificationResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/notifications/type/LIKE"))
                .andExpect(status().isOk());
    }
}
