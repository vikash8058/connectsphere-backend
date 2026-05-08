package com.connectsphere.notification.controller;

import com.connectsphere.notification.dto.*;
import com.connectsphere.notification.service.ReportService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportResource Controller Tests")
class ReportResourceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private ReportService reportService;
    @InjectMocks private ReportResource reportResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportResource).build();
    }

    @Test
    void createReport() throws Exception {
        CreateReportRequestDTO req = CreateReportRequestDTO.builder()
                .targetId(1).targetType("POST").reason("Spam").build();
        when(reportService.createReport(anyInt(), any()))
                .thenReturn(ApiResponseDTO.<ReportResponseDTO>builder().success(true).build());
        mockMvc.perform(post("/reports")
                        .requestAttr("requestingUserId", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void getPendingReports() throws Exception {
        when(reportService.getPendingReports())
                .thenReturn(ApiResponseDTO.<List<ReportResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/reports/staff"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllReports() throws Exception {
        when(reportService.getAllReports())
                .thenReturn(ApiResponseDTO.<List<ReportResponseDTO>>builder().success(true).build());
        mockMvc.perform(get("/reports/all"))
                .andExpect(status().isOk());
    }

    @Test
    void resolveReport() throws Exception {
        when(reportService.resolveReport(1, "RESOLVE"))
                .thenReturn(ApiResponseDTO.<String>builder().success(true).build());
        mockMvc.perform(put("/reports/1/resolve").param("action", "RESOLVE"))
                .andExpect(status().isOk());
    }

    @Test
    void getReportStats() throws Exception {
        when(reportService.getReportStats())
                .thenReturn(ApiResponseDTO.<ReportStatsDTO>builder().success(true).build());
        mockMvc.perform(get("/reports/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAllReports() throws Exception {
        doNothing().when(reportService).deleteAllReports();
        mockMvc.perform(delete("/reports/all"))
                .andExpect(status().isOk());
    }
}
