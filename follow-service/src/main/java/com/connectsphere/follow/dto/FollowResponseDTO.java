package com.connectsphere.follow.dto;

import com.connectsphere.follow.entity.FollowStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * FollowResponseDTO - Safe follow data returned in API responses
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowResponseDTO {

    private Integer followId;
    private Integer followerId;
    private Integer followeeId;
    private FollowStatus status;
    private LocalDateTime createdAt;
}