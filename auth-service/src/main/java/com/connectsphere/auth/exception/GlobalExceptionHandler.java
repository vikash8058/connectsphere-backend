package com.connectsphere.auth.exception;

import com.connectsphere.auth.dto.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler - Centralized exception handling for Auth Service
 *
 * Converts exceptions into consistent ApiResponseDTO error responses. Prevents
 * raw stack traces from leaking to API consumers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
		log.warn("UserAlreadyExists: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleUserNotFound(UserNotFoundException ex) {
		log.warn("UserNotFound: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
		log.warn("InvalidCredentials: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	@ExceptionHandler(InvalidOtpException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleInvalidOtp(InvalidOtpException ex) {
		log.warn("InvalidOtp: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("IllegalArgument: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleValidationErrors(
			MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			String field = ((FieldError) error).getField();
			String message = error.getDefaultMessage();
			errors.put(field, message);
		});
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponseDTO.<Map<String, String>>builder()
				.success(false)
				.message("Validation failed")
				.data(errors).build());
	}

	@ExceptionHandler(UnauthorizedAccessException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
		log.warn("UnauthorizedAccess: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponseDTO.error(ex.getMessage()));
	}

	/**
	 * Handles Spring Security @PreAuthorize failures (403 Forbidden).
	 * Without this, Spring returns a default 403 page instead of our ApiResponseDTO.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleAccessDenied(AccessDeniedException ex) {
		log.warn("AccessDenied: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponseDTO.error("Access denied: you do not have permission to perform this action."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponseDTO<Void>> handleGenericException(Exception ex) {
		log.error("Unhandled exception: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponseDTO.error("An unexpected error occurred. Please try again."));
	}
}