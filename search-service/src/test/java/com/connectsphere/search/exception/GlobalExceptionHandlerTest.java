package com.connectsphere.search.exception;

import com.connectsphere.search.controller.SearchResource;
import com.connectsphere.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchResource searchResource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleHashtagNotFound() throws Exception {
        when(searchService.getPostsByHashtag(anyString()))
                .thenThrow(new HashtagNotFoundException("Not found"));

        mockMvc.perform(get("/hashtags/unknown/posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void handleIllegalArgument() throws Exception {
        when(searchService.getTrendingHashtags(anyInt()))
                .thenThrow(new IllegalArgumentException("Invalid limit"));

        mockMvc.perform(get("/hashtags/trending").param("limit", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid limit"));
    }

    @Test
    void handleValidationErrors() {
        // We use MockMvc to trigger a validation error if possible, 
        // or just call the handler directly for unit test coverage.
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError error = new org.springframework.validation.FieldError("dto", "field", "must not be null");
        when(bindingResult.getAllErrors()).thenReturn(List.of(error));
        
        org.springframework.web.bind.MethodArgumentNotValidException ex = 
                new org.springframework.web.bind.MethodArgumentNotValidException(null, bindingResult);
        
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleValidationErrors(ex);
        
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertEquals("must not be null", response.getBody().getData().get("field"));
    }

    @Test
    void handleGenericException() throws Exception {
        when(searchService.getHashtagCount(anyString()))
                .thenThrow(new RuntimeException("Server error"));

        mockMvc.perform(get("/hashtags/java/count"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again."));
    }
}
