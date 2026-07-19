package com.ecommerce.auth.adapter.in.rest;

import com.ecommerce.auth.adapter.in.rest.dto.LoginRequest;
import com.ecommerce.auth.adapter.in.rest.dto.LoginResponse;
import com.ecommerce.auth.adapter.in.rest.dto.RegisterRequest;
import com.ecommerce.auth.adapter.in.rest.dto.UserResponse;
import com.ecommerce.auth.application.command.LoginCommand;
import com.ecommerce.auth.application.command.RegisterCommand;
import com.ecommerce.auth.application.service.LoginUseCase;
import com.ecommerce.auth.application.service.RegisterUserUseCase;
import com.ecommerce.auth.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;

    public AuthController(RegisterUserUseCase registerUser, LoginUseCase login) {
        this.registerUser = registerUser;
        this.login = login;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUser.handle(
                new RegisterCommand(request.email(), request.password(), request.displayName()));
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginUseCase.AuthenticatedUser authenticated =
                login.handle(new LoginCommand(request.email(), request.password()));
        return new LoginResponse(
                authenticated.token().token(),
                authenticated.token().expiresAt(),
                UserResponse.from(authenticated.user()));
    }
}
