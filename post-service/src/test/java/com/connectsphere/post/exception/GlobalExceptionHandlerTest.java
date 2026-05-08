package com.connectsphere.post.exception;

import com.connectsphere.post.dto.ApiResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlePostNotFound() {
        PostNotFoundException ex = new PostNotFoundException("Not found");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handlePostNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleUnauthorized() {
        UnauthorizedActionException ex = new UnauthorizedActionException("Denied");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleUnauthorized(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad arg");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleIllegalArgument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handleGenericException() {
        Exception ex = new Exception("Error");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleGenericException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
