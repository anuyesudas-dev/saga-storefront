package com.ecommerce.auth.adapter.in.rest;

import com.ecommerce.auth.application.DuplicateEmailException;
import com.ecommerce.auth.application.InvalidCredentialsException;
import com.ecommerce.common.web.ApiExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail onDuplicateEmail(DuplicateEmailException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail onInvalidCredentials(InvalidCredentialsException e) {
        // identical body for unknown email and wrong password — no user enumeration
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid credentials");
        return problem;
    }
}
