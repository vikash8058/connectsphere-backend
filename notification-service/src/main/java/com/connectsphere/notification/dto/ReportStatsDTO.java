package com.connectsphere.notification.dto;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportStatsDTO {
    private long total;
    private long pending;
    private long resolved;
    private long dismissed;
}
