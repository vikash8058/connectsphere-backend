package com.connectsphere.notification.controller;

import com.connectsphere.notification.dto.ApiResponseDTO;
import com.connectsphere.notification.dto.CreateReportRequestDTO;
import com.connectsphere.notification.dto.ReportResponseDTO;
import com.connectsphere.notification.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reports")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Report Service", description = "Endpoints for content moderation reports")
public class ReportResource {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Submit a content report", description = "Any authenticated user can report a post, comment, or user.")
    public ResponseEntity<ApiResponseDTO<ReportResponseDTO>> createReport(
            @Valid @RequestBody CreateReportRequestDTO request,
            HttpServletRequest httpRequest) {
        Integer userId = getRequestingUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createReport(userId, request));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get pending reports (Staff only)", description = "Returns all reports with status PENDING.")
    public ResponseEntity<ApiResponseDTO<List<ReportResponseDTO>>> getPendingReports() {
        return ResponseEntity.ok(reportService.getPendingReports());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get all reports (Staff only)", description = "Returns all reports for history view.")
    public ResponseEntity<ApiResponseDTO<List<ReportResponseDTO>>> getAllReports() {
        return ResponseEntity.ok(reportService.getAllReports());
    }

    @PutMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Resolve a report (Staff only)", description = "Mark report as RESOLVED or DISMISSED.")
    public ResponseEntity<ApiResponseDTO<String>> resolveReport(
            @PathVariable Integer reportId,
            @RequestParam String action) {
        return ResponseEntity.ok(reportService.resolveReport(reportId, action));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get report statistics (Staff only)", description = "Returns counts of total, pending, resolved, and dismissed reports.")
    public ResponseEntity<ApiResponseDTO<com.connectsphere.notification.dto.ReportStatsDTO>> getReportStats() {
        return ResponseEntity.ok(reportService.getReportStats());
    }

    @DeleteMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all reports (Admin only)", description = "Resets all report counts to zero.")
    public ResponseEntity<ApiResponseDTO<String>> deleteAllReports() {
        reportService.deleteAllReports();
        return ResponseEntity.ok(ApiResponseDTO.success("All reports cleared successfully"));
    }

    private Integer getRequestingUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("requestingUserId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized: userId not found in request");
        }
        try {
            return Integer.valueOf(userId.toString());
        } catch (Exception e) {
            return (Integer) userId;
        }
    }
}
