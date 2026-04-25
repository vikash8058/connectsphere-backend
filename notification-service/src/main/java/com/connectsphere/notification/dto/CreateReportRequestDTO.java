package com.connectsphere.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReportRequestDTO {
    private Integer targetId;
    private String targetType;
    private String reason;
}
