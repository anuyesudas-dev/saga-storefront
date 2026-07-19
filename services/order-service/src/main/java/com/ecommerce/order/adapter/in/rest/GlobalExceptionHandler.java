package com.ecommerce.order.adapter.in.rest;

import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain-specific problem mappings; validation and fallback handlers are
 * inherited from the shared {@link ApiExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail onOrderNotFound(OrderNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Order not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ProblemDetail onIllegalState(IllegalOrderStateException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Illegal order state transition");
        problem.setDetail(e.getMessage());
        problem.setProperty("currentStatus", e.getCurrentStatus());
        return problem;
    }
}
