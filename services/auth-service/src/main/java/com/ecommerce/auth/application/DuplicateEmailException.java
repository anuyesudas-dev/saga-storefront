package com.ecommerce.auth.application;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("email already registered");
    }
}
