package com.openclassrooms.tourguide.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<Object> handleLocationNotFoundException(LocationNotFoundException e) {

        Map<String, Object> bodyIndications = new HashMap<>();
        bodyIndications.put("timestamp", LocalDateTime.now());
        bodyIndications.put("httpStatus", HttpStatus.SERVICE_UNAVAILABLE.value());
        bodyIndications.put("error", "Service Unavailable");
        bodyIndications.put("message", e.getMessage());

        return new ResponseEntity<>(bodyIndications, HttpStatus.SERVICE_UNAVAILABLE);
    }








}
