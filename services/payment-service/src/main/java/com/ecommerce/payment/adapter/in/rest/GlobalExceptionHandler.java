package com.ecommerce.payment.adapter.in.rest;

import com.ecommerce.common.web.ApiExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** No payment-specific REST exceptions yet — shared handlers cover this service. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {
}
