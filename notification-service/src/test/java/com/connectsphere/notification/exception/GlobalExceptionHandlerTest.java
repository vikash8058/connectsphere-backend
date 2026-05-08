package com.connectsphere.notification.exception;

import com.connectsphere.notification.dto.ApiResponseDTO;
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
    void handleNotificationNotFound() {
        NotificationNotFoundException ex = new NotificationNotFoundException("Not found");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleNotificationNotFound(ex);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleUnauthorized() {
        UnauthorizedActionException ex = new UnauthorizedActionException("Forbidden");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleUnauthorized(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test
    void handleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleIllegalArgument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void handleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "recipientId", "is required"));

        MethodParameter param = new MethodParameter(
                this.getClass().getDeclaredMethod("handleValidationErrors"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().getData().containsKey("recipientId"));
    }

    @Test
    void handleGenericException() {
        Exception ex = new Exception("Error");
        ResponseEntity<ApiResponseDTO<Void>> resp = handler.handleGenericException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
