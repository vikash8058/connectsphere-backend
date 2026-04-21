package com.connectsphere.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * PostListApiResponse - Wrapper matching post-service ApiResponseDTO<List<PostResponseDTO>>
 * Used by searchPosts() Feign call.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostListApiResponse {
    private boolean success;
    private String message;
    private List<PostDataDTO> data;
}
