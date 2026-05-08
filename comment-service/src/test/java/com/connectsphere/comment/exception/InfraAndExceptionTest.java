package com.connectsphere.comment.exception;

import com.connectsphere.comment.config.FeignConfig;
import com.connectsphere.comment.dto.ApiResponseDTO;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfraAndExceptionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final FeignConfig feignConfig = new FeignConfig();
    
    @Test
    void handleValidationErrors_WithActualErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "content", "must not be blank");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().getData().containsKey("content"));
        assertEquals("must not be blank", resp.getBody().getData().get("content"));
    }

    @Test
    void handleCommentNotFound() {
        CommentNotFoundException ex = new CommentNotFoundException("Not found");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleCommentNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handlePostNotFound() {
        PostNotFoundException ex = new PostNotFoundException("Post not found");
        ResponseEntity<ApiResponseDTO<String>> resp = handler.handlePostNotFoundException(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleUnauthorized() {
        UnauthorizedActionException ex = new UnauthorizedActionException("Denied");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleUnauthorized(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad Request");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleIllegalArgument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handlePostServiceUnavailable() {
        PostServiceUnavailableException ex = new PostServiceUnavailableException("Down");
        ResponseEntity<ApiResponseDTO<String>> resp = handler.handlePostServiceUnavailableException(ex);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
    }

    @Test
    void handleValidationErrors() {
        // We mock the MethodArgumentNotValidException to trigger the validator handler
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(new ArrayList<>());

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Validation failed", resp.getBody().getMessage());
    }

    @Test
    void handleGenericException() {
        Exception ex = new Exception("Error");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleGenericException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }

    @Test
    void testFeignConfig() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("X-User-Id")).thenReturn("1");
        
        RequestTemplate template = new RequestTemplate();
        feignConfig.requestInterceptor().apply(template);
        
        assertTrue(template.headers().get("X-User-Id").contains("1"));
        RequestContextHolder.resetRequestAttributes();
    }
}
