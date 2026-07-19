package com.ecommerce.auth.application;

/** One exception for unknown email AND wrong password — responses must not reveal which. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
