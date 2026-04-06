package com.zamipter.EphemeralSharingService.Exception;

import com.zamipter.EphemeralSharingService.DTO.CustomErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.crypto.AEADBadTagException;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle Rate Limiting (429 Too Many Requests)
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<CustomErrorResponse> handleRateLimit(RateLimitException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Rate Limit Exceeded", ex.getMessage());
    }

    // 2. Handle Wrong Password or Corrupt Key (401 Unauthorized)
    @ExceptionHandler(AEADBadTagException.class)
    public ResponseEntity<CustomErrorResponse> handleDecryptionError(AEADBadTagException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Security Error", "Incorrect password or invalid decryption key.");
    }

    // 3. Handle "Not Found" errors (404 Not Found)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CustomErrorResponse> handleRuntime(RuntimeException ex) {
        // If the message contains "Not Found", use 404, otherwise 400
        HttpStatus status = ex.getMessage().contains("Not Found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return buildResponse(status, status.getReasonPhrase(), ex.getMessage());
    }

    // 4. Handle Unexpected System Errors (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomErrorResponse> handleGeneral(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", "An unexpected error occurred on the Salem server.");
    }

    // Helper method to keep code DRY (Don't Repeat Yourself)
    private ResponseEntity<CustomErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        CustomErrorResponse response = new CustomErrorResponse(
            status.value(),
            error,
            message,
            LocalDateTime.now()
        );
        return new ResponseEntity<>(response, status);
    }
}
