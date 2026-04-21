package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * PostApiResponse - Wrapper matching post-service ApiResponseDTO<PostResponseDTO>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostApiResponse {
    private boolean success;
    private String message;
    private PostDataDTO data;
}

