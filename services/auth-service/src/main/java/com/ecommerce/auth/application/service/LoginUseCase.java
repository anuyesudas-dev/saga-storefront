package com.ecommerce.auth.application.service;

import com.ecommerce.auth.application.InvalidCredentialsException;
import com.ecommerce.auth.application.command.LoginCommand;
import com.ecommerce.auth.application.port.TokenIssuer;
import com.ecommerce.auth.application.port.TokenIssuer.IssuedToken;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LoginUseCase {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final String timingEqualizerHash;
    private final Counter loginSuccess;
    private final Counter loginFailure;

    public LoginUseCase(UserRepository users, PasswordEncoder passwordEncoder,
                        TokenIssuer tokenIssuer, MeterRegistry meterRegistry) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        // hashed once so the unknown-email path still pays a bcrypt comparison —
        // otherwise response time reveals whether the email exists
        this.timingEqualizerHash = passwordEncoder.encode(UUID_LIKE_FILLER);
        this.loginSuccess = meterRegistry.counter("auth.logins", "outcome", "success");
        this.loginFailure = meterRegistry.counter("auth.logins", "outcome", "failure");
    }

    private static final String UUID_LIKE_FILLER = "timing-equalizer-not-a-real-password";

    public AuthenticatedUser handle(LoginCommand command) {
        Optional<User> user = users.findByEmail(RegisterUserUseCase.normalize(command.email()));
        boolean matches = user
                .map(u -> passwordEncoder.matches(command.password(), u.getPasswordHash()))
                .orElseGet(() -> {
                    passwordEncoder.matches(command.password(), timingEqualizerHash);
                    return false;
                });
        if (!matches) {
            loginFailure.increment();
            throw new InvalidCredentialsException();
        }
        loginSuccess.increment();
        return new AuthenticatedUser(user.get(), tokenIssuer.issue(user.get()));
    }

    public record AuthenticatedUser(User user, IssuedToken token) {
    }
}
