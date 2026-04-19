package com.connectsphere.comment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostApiResponse {
    private boolean success;
    private String message;
    private PostResponseDTO data;
}