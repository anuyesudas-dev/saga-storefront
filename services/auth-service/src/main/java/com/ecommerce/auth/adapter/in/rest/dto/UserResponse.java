package com.ecommerce.auth.adapter.in.rest.dto;

import com.ecommerce.auth.domain.model.User;

public record UserResponse(String userId, String email, String displayName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
