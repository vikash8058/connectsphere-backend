package com.connectsphere.media.exception;

import com.connectsphere.media.dto.ApiResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMediaNotFound() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleMediaNotFound(new MediaNotFoundException("Not found"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleStoryNotFound() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleStoryNotFound(new StoryNotFoundException("Not found"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleUnauthorized() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleUnauthorized(new UnauthorizedActionException("Forbidden"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleUnsupportedMediaType() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleUnsupportedMediaType(new UnsupportedMediaTypeException("Unsupported"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, resp.getStatusCode());
    }

    @Test
    void handleIllegalArgument() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handlePostNotFound() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handlePostNotFound(new PostNotFoundException("Post missing"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleMaxUploadSizeExceeded() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resp.getStatusCode());
    }

    @Test
    void handleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "mediaUrl", "is required"));
        MethodParameter param = new MethodParameter(
                this.getClass().getDeclaredMethod("handleValidationErrors"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, binding);

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().getData().containsKey("mediaUrl"));
    }

    @Test
    void handleGenericException() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleGenericException(new Exception("Unexpected"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
