package com.ecommerce.auth.application.service;

import com.ecommerce.auth.application.DuplicateEmailException;
import com.ecommerce.auth.application.command.RegisterCommand;
import com.ecommerce.auth.domain.model.User;
import com.ecommerce.auth.domain.port.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class RegisterUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserUseCase.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Counter registrations;

    public RegisterUserUseCase(UserRepository users, PasswordEncoder passwordEncoder,
                               Clock clock, MeterRegistry meterRegistry) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.registrations = meterRegistry.counter("auth.registrations");
    }

    public User handle(RegisterCommand command) {
        String email = normalize(command.email());
        if (users.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }
        User user = User.register(UUID.randomUUID().toString(), email,
                passwordEncoder.encode(command.password()),
                command.displayName().trim(), clock.instant());
        try {
            User saved = users.save(user);
            registrations.increment();
            log.info("registered user {}", saved.getId());
            return saved;
        } catch (DuplicateKeyException e) {
            // two registrations raced past the check — the unique index decides
            throw new DuplicateEmailException();
        }
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
