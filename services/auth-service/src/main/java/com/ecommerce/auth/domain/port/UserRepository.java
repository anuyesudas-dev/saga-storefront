package com.ecommerce.auth.domain.port;

import com.ecommerce.auth.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findByEmail(String email);
}
