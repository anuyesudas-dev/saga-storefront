package com.ecommerce.notification.adapter.in.rest;

import com.ecommerce.common.web.ApiExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Shared handlers cover this read-only API. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {
}
