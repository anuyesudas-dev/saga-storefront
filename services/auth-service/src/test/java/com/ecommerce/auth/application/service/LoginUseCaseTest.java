package com.ecommerce.auth.application.service;

import com.ecommerce.auth.application.InvalidCredentialsException;
import com.ecommerce.auth.application.command.LoginCommand;
import com.ecommerce.auth.application.port.TokenIssuer;
import com.ecommerce.auth.application.port.TokenIssuer.IssuedToken;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private LoginUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(users, encoder, tokens, meters);
        user = User.register("user-1", "anu@example.com",
                encoder.encode("s3cret-password"), "Anu", NOW);
    }

    @Test
    void validCredentialsIssueToken() {
        when(users.findByEmail("anu@example.com")).thenReturn(Optional.of(user));
        when(tokens.issue(user)).thenReturn(new IssuedToken("jwt", NOW.plusSeconds(1800)));

        LoginUseCase.AuthenticatedUser result =
                useCase.handle(new LoginCommand("Anu@Example.com", "s3cret-password"));

        assertThat(result.token().token()).isEqualTo("jwt");
        assertThat(result.user().getId()).isEqualTo("user-1");
        assertThat(meters.counter("auth.logins", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void wrongPasswordIsRejectedWithoutIssuingToken() {
        when(users.findByEmail("anu@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.handle(
                new LoginCommand("anu@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(any());
        assertThat(meters.counter("auth.logins", "outcome", "failure").count()).isEqualTo(1);
    }

    @Test
    void unknownEmailFailsWithTheSameException() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.handle(
                new LoginCommand("ghost@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(any());
    }
}
