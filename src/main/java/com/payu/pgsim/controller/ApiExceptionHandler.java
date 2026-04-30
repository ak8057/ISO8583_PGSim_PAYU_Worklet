package com.payu.pgsim.controller;

import com.payu.pgsim.handler.ScenarioTimeoutException;
import com.payu.pgsim.validator.IsoValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IsoValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IsoValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "VALIDATION_ERROR",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(ScenarioTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(ScenarioTimeoutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                "error", "TIMEOUT",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", e.getMessage()
        ));
    }
}

