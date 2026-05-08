package com.connectsphere.follow.exception;

import com.connectsphere.follow.dto.ApiResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleFollowNotFound() {
        FollowNotFoundException ex = new FollowNotFoundException("Not found");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleFollowNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleAlreadyFollowing() {
        AlreadyFollowingException ex = new AlreadyFollowingException("Already following");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleAlreadyFollowing(ex);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleSelfFollow() {
        SelfFollowException ex = new SelfFollowException("Self follow");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleSelfFollow(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad arg");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleIllegalArgument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handleUserNotFound() {
        UserNotFoundException ex = new UserNotFoundException("User missing");
        ResponseEntity<ApiResponseDTO<String>> resp = handler.handleUserNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "followeeId", "is required"));

        MethodParameter param = new MethodParameter(
                this.getClass().getDeclaredMethod("handleValidationErrors"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().getData().containsKey("followeeId"));
    }

    @Test
    void handleGenericException() {
        Exception ex = new Exception("Error");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleGenericException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
