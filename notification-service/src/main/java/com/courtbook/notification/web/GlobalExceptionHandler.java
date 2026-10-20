package com.courtbook.notification.web;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the service as an RFC 9457 ProblemDetail. Validation and
 * other Spring MVC errors are handled by the parent class in the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        body.setTitle(e.title());
        return ResponseEntity.status(e.status()).body(body);
    }
}
