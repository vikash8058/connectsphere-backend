package com.connectsphere.auth.exception;

import com.connectsphere.auth.dto.ApiResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler — Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setup() {
        handler = new GlobalExceptionHandler();
    }

    @Test @DisplayName("UserAlreadyExistsException → 409 CONFLICT")
    void handleUserAlreadyExists_returns409() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleUserAlreadyExists(new UserAlreadyExistsException("Email already in use"));
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("Email already in use", resp.getBody().getMessage());
    }

    @Test @DisplayName("UserNotFoundException → 404 NOT FOUND")
    void handleUserNotFound_returns404() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleUserNotFound(new UserNotFoundException("User not found"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test @DisplayName("InvalidCredentialsException → 401 UNAUTHORIZED")
    void handleInvalidCredentials_returns401() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleInvalidCredentials(new InvalidCredentialsException("Bad credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test @DisplayName("InvalidOtpException → 400 BAD REQUEST")
    void handleInvalidOtp_returns400() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleInvalidOtp(new InvalidOtpException("OTP expired"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test @DisplayName("IllegalArgumentException → 400 BAD REQUEST")
    void handleIllegalArgument_returns400() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("Invalid role"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("Invalid role", resp.getBody().getMessage());
    }

    @Test @DisplayName("UnauthorizedAccessException → 403 FORBIDDEN")
    void handleUnauthorizedAccess_returns403() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleUnauthorizedAccess(new UnauthorizedAccessException("Cannot delete yourself"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test @DisplayName("AccessDeniedException → 403 FORBIDDEN")
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleAccessDenied(new AccessDeniedException("Access is denied"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertTrue(resp.getBody().getMessage().contains("Access denied"));
    }

    @Test @DisplayName("Generic Exception → 500 INTERNAL SERVER ERROR")
    void handleGenericException_returns500() {
        ResponseEntity<ApiResponseDTO<Void>> resp =
                handler.handleGenericException(new RuntimeException("Something broke"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
    }

    @Test @DisplayName("MethodArgumentNotValidException → 400 with field errors map")
    void handleValidationErrors_returns400WithFields() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Validation failed", resp.getBody().getMessage());
        assertEquals("must not be blank", resp.getBody().getData().get("email"));
    }

    @Test @DisplayName("MethodArgumentNotValidException — multiple fields captured")
    void handleValidationErrors_multipleFields() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "invalid email"));
        bindingResult.addError(new FieldError("registerRequest", "password", "too short"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponseDTO<Map<String, String>>> resp = handler.handleValidationErrors(ex);

        assertEquals(2, resp.getBody().getData().size());
    }
}