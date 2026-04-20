package com.connectsphere.follow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // ignore extra fields we don't need
public class UserExistsResponseDTO {
    private boolean success;
    private String message;
    private UserDataDTO data;   // we only need to know data is not null
}