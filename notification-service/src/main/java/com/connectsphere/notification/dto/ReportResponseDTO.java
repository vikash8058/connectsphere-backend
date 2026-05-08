package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
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
