package dev.fluxguard.orders;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException error, HttpServletRequest request) {
        return body(error.getStatusCode().value(), error.getReason(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        return body(400, "Invalid request body", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException error, HttpServletRequest request) {
        return body(403, "Insufficient role", request);
    }

    private ResponseEntity<Map<String, Object>> body(int code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", code,
            "error", HttpStatus.valueOf(code).getReasonPhrase(),
            "message", message == null ? "Request failed" : message,
            "path", request.getRequestURI()));
    }
}
