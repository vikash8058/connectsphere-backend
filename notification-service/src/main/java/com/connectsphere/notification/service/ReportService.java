package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.ApiResponseDTO;
import com.connectsphere.notification.dto.CreateReportRequestDTO;
import com.connectsphere.notification.dto.ReportResponseDTO;
import com.connectsphere.notification.dto.ReportStatsDTO;

import java.util.List;

public interface ReportService {
    ApiResponseDTO<ReportResponseDTO> createReport(Integer reporterId, CreateReportRequestDTO request);
    ApiResponseDTO<List<ReportResponseDTO>> getPendingReports();
    ApiResponseDTO<List<ReportResponseDTO>> getAllReports();
    ApiResponseDTO<String> resolveReport(Integer reportId, String action);

    ApiResponseDTO<ReportStatsDTO> getReportStats();
    void deleteAllReports();
}
