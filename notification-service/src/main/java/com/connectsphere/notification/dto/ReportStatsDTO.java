package com.connectsphere.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatsDTO {
    private long total;
    private long pending;
    private long resolved;
    private long dismissed;
}
