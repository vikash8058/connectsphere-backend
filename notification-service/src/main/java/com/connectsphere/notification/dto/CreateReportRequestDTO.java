package com.connectsphere.notification.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReportRequestDTO {
    private Integer targetId;
    private String targetType;
    private String reason;
}
