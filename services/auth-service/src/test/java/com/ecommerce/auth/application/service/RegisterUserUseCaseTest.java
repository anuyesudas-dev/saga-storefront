package com.ecommerce.auth.application.service;

import com.ecommerce.auth.application.DuplicateEmailException;
import com.ecommerce.auth.application.command.RegisterCommand;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    // strength 4: fastest legal cost — unit tests shouldn't pay production bcrypt time
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(users, encoder,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void hashesPasswordAndNormalizesEmail() {
        User user = useCase.handle(
                new RegisterCommand("  Anu@Example.COM ", "s3cret-password", "  Anu  "));

        assertThat(user.getEmail()).isEqualTo("anu@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Anu");
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getPasswordHash()).isNotEqualTo("s3cret-password");
        assertThat(encoder.matches("s3cret-password", user.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.findByEmail("anu@example.com"))
                .thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> useCase.handle(
                new RegisterCommand("anu@example.com", "s3cret-password", "Anu")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void concurrentRegistrationRaceSurfacesAsDuplicateEmail() {
        // check passed (no user yet) but the unique index fired on save
        when(users.save(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> useCase.handle(
                new RegisterCommand("anu@example.com", "s3cret-password", "Anu")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    private User existingUser() {
        return User.register("user-1", "anu@example.com",
                encoder.encode("other"), "Anu", NOW);
    }
}
