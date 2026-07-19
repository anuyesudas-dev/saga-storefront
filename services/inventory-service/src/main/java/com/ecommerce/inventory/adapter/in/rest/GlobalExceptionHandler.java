package com.ecommerce.inventory.adapter.in.rest;

import com.ecommerce.common.web.ApiExceptionHandler;
import com.ecommerce.inventory.domain.exception.ProductAlreadyExistsException;
import com.ecommerce.inventory.domain.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail onProductNotFound(ProductNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Product not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ProblemDetail onProductAlreadyExists(ProductAlreadyExistsException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Product already exists");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
