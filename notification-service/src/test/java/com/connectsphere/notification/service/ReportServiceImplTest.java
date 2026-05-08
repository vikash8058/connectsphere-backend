package com.connectsphere.notification.service;

import com.connectsphere.notification.client.AuthClient;
import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.Report;
import com.connectsphere.notification.entity.ReportStatus;
import com.connectsphere.notification.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private AuthClient authClient;
    @Mock private NotificationService notificationService;
    @InjectMocks private ReportServiceImpl reportService;

    @Test
    void createReport() {
        CreateReportRequestDTO req = CreateReportRequestDTO.builder()
                .targetId(1).targetType("POST").reason("Spam").build();
        Report saved = Report.builder().reportId(1).reporterId(5).targetId(1)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.save(any())).thenReturn(saved);

        ApiResponseDTO<ReportResponseDTO> resp = reportService.createReport(5, req);
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().getReportId());
    }

    @Test
    void getPendingReports() {
        Report r = Report.builder().reportId(1).reporterId(5).targetId(1)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(List.of(r));
        when(authClient.getUserById(5)).thenReturn(
                new UserDataDTO(true, new UserDataDTO.UserDTO(5, "user5", "User 5", "u@b.com", null)));

        ApiResponseDTO<List<ReportResponseDTO>> resp = reportService.getPendingReports();
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
        assertEquals("user5", resp.getData().get(0).getReporterUsername());
    }

    @Test
    void getPendingReports_enrichFails() {
        Report r = Report.builder().reportId(1).reporterId(5).targetId(1)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(List.of(r));
        when(authClient.getUserById(5)).thenThrow(new RuntimeException("Feign down"));

        ApiResponseDTO<List<ReportResponseDTO>> resp = reportService.getPendingReports();
        assertTrue(resp.isSuccess());
        assertEquals("User #5", resp.getData().get(0).getReporterUsername());
    }

    @Test
    void getAllReports() {
        when(reportRepository.findAll(any(Sort.class))).thenReturn(List.of());
        ApiResponseDTO<List<ReportResponseDTO>> resp = reportService.getAllReports();
        assertTrue(resp.isSuccess());
    }

    @Test
    void resolveReport_resolve() {
        Report r = Report.builder().reportId(1).reporterId(5).targetId(10)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.findById(1)).thenReturn(Optional.of(r));
        when(reportRepository.save(any())).thenReturn(r);
        when(notificationService.createNotification(any()))
                .thenReturn(ApiResponseDTO.<NotificationResponseDTO>builder().success(true).build());

        ApiResponseDTO<String> resp = reportService.resolveReport(1, "RESOLVE");
        assertTrue(resp.isSuccess());
        assertEquals(ReportStatus.RESOLVED, r.getStatus());
    }

    @Test
    void resolveReport_dismiss() {
        Report r = Report.builder().reportId(1).reporterId(5).targetId(10)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.findById(1)).thenReturn(Optional.of(r));
        when(reportRepository.save(any())).thenReturn(r);
        when(notificationService.createNotification(any()))
                .thenReturn(ApiResponseDTO.<NotificationResponseDTO>builder().success(true).build());

        ApiResponseDTO<String> resp = reportService.resolveReport(1, "DISMISS");
        assertTrue(resp.isSuccess());
        assertEquals(ReportStatus.DISMISSED, r.getStatus());
    }

    @Test
    void resolveReport_notificationFails() {
        Report r = Report.builder().reportId(1).reporterId(5).targetId(10)
                .targetType("POST").reason("Spam").status(ReportStatus.PENDING).build();
        when(reportRepository.findById(1)).thenReturn(Optional.of(r));
        when(reportRepository.save(any())).thenReturn(r);
        when(notificationService.createNotification(any()))
                .thenThrow(new RuntimeException("Notification failed"));

        ApiResponseDTO<String> resp = reportService.resolveReport(1, "RESOLVE");
        assertTrue(resp.isSuccess());
    }

    @Test
    void resolveReport_notFound() {
        when(reportRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> reportService.resolveReport(99, "RESOLVE"));
    }

    @Test
    void getReportStats() {
        when(reportRepository.count()).thenReturn(10L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(3L);
        when(reportRepository.countByStatus(ReportStatus.RESOLVED)).thenReturn(5L);
        when(reportRepository.countByStatus(ReportStatus.DISMISSED)).thenReturn(2L);

        ApiResponseDTO<ReportStatsDTO> resp = reportService.getReportStats();
        assertTrue(resp.isSuccess());
        assertEquals(10, resp.getData().getTotal());
    }

    @Test
    void deleteAllReports() {
        doNothing().when(reportRepository).deleteAll();
        reportService.deleteAllReports();
        verify(reportRepository).deleteAll();
    }
}
