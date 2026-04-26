package com.connectsphere.notification.service;

import com.connectsphere.notification.client.AuthClient;
import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.Report;
import com.connectsphere.notification.entity.ReportStatus;
import com.connectsphere.notification.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final AuthClient authClient;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ApiResponseDTO<ReportResponseDTO> createReport(Integer reporterId, CreateReportRequestDTO request) {
        log.info("Creating report by userId: {} on {} id: {}", reporterId, request.getTargetType(), request.getTargetId());

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .reason(request.getReason())
                .status(ReportStatus.PENDING)
                .build();

        Report saved = reportRepository.save(report);
        return ApiResponseDTO.success("Report submitted successfully", toDTO(saved));
    }

    @Override
    public ApiResponseDTO<List<ReportResponseDTO>> getPendingReports() {
        log.debug("Fetching all pending reports");
        List<ReportResponseDTO> reports = reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        // Enrich with reporter usernames
        enrichWithReporterInfo(reports);
        
        return ApiResponseDTO.success("Pending reports fetched", reports);
    }

    @Override
    public ApiResponseDTO<List<ReportResponseDTO>> getAllReports() {
        log.debug("Fetching all reports for history");
        List<ReportResponseDTO> reports = reportRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        enrichWithReporterInfo(reports);
        return ApiResponseDTO.success("All reports fetched", reports);
    }

    @Override
    @Transactional
    public ApiResponseDTO<String> resolveReport(Integer reportId, String action) {
        log.info("Resolving report id: {} with action: {}", reportId, action);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        boolean isDismiss = "DISMISS".equalsIgnoreCase(action);
        if (isDismiss) {
            report.setStatus(ReportStatus.DISMISSED);
        } else {
            report.setStatus(ReportStatus.RESOLVED);
        }

        Report updated = reportRepository.save(report);

        // Notify the reporter about the resolution
        try {
            String msg = isDismiss 
                ? "Your report on " + report.getTargetType() + " #" + report.getTargetId() + " was reviewed and dismissed."
                : "Your report on " + report.getTargetType() + " #" + report.getTargetId() + " was resolved and the content has been moderated.";
            
            notificationService.createNotification(
                CreateNotificationRequestDTO.builder()
                    .recipientId(report.getReporterId())
                    .actorId(0) // System
                    .type(NotificationType.SYSTEM)
                    .message(msg)
                    .targetId(reportId)
                    .targetType("REPORT")
                    .deepLinkUrl("/admin/reports")
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to notify reporter of resolution: {}", e.getMessage());
        }

        return ApiResponseDTO.success("Report " + report.getStatus().name().toLowerCase());
    }

    @Override
    public ApiResponseDTO<ReportStatsDTO> getReportStats() {
        log.debug("Calculating report statistics");
        long total = reportRepository.count();
        long pending = reportRepository.countByStatus(ReportStatus.PENDING);
        long resolved = reportRepository.countByStatus(ReportStatus.RESOLVED);
        long dismissed = reportRepository.countByStatus(ReportStatus.DISMISSED);

        return ApiResponseDTO.success("Stats fetched", ReportStatsDTO.builder()
                .total(total)
                .pending(pending)
                .resolved(resolved)
                .dismissed(dismissed)
                .build());
    }

    @Override
    public void deleteAllReports() {
        log.info("Deleting all reports from the database");
        reportRepository.deleteAll();
    }

    private void enrichWithReporterInfo(List<ReportResponseDTO> dtos) {
        for (ReportResponseDTO dto : dtos) {
            try {
                UserDataDTO res = authClient.getUserById(dto.getReporterId());
                if (res != null && res.getData() != null) {
                    dto.setReporterUsername(res.getData().getUsername());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch reporter info for report id: {}", dto.getReportId());
                dto.setReporterUsername("User #" + dto.getReporterId());
            }
        }
    }

    private ReportResponseDTO toDTO(Report report) {
        return ReportResponseDTO.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporterId())
                .targetId(report.getTargetId())
                .targetType(report.getTargetType())
                .reason(report.getReason())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
