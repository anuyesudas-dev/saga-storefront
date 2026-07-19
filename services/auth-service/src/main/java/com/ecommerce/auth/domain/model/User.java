package com.ecommerce.auth.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Thin write-once aggregate: an account is created and then only read.
 * The unique email index is the invariant — two registrations can race past
 * the application-level check, but never past the index.
 */
@Document(collection = "users")
public class User {

    @Id
    private final String id;
    @Indexed(unique = true)
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final Instant createdAt;
    @Version
    private Long version;

    @PersistenceCreator
    User(String id, String email, String passwordHash, String displayName,
         Instant createdAt, Long version) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static User register(String id, String email, String passwordHash,
                                String displayName, Instant now) {
        return new User(id, email, passwordHash, displayName, now, null);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
