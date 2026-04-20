package com.connectsphere.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ApiResponseDTO - Generic API response wrapper
 *
 * Identical structure across all ConnectSphere microservices.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseDTO<T> {

    private boolean success;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponseDTO<T> success(String message, T data) {
        return ApiResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponseDTO<T> success(String message) {
        return ApiResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static <T> ApiResponseDTO<T> error(String message) {
        return ApiResponseDTO.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}