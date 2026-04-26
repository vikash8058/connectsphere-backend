package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponseDTO {
    private Integer reportId;
    private Integer reporterId;
    private String reporterUsername;
    private Integer targetId;
    private String targetType;
    private String reason;
    private ReportStatus status;
    private LocalDateTime createdAt;
}
