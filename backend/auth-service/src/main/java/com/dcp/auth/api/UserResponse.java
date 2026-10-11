package com.dcp.auth.api;

import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Set<String> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        Set<String> roles = new TreeSet<>();
        for (Role role : user.getRoles()) {
            roles.add(role.name());
        }
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), Set.copyOf(roles), user.getCreatedAt());
    }
}
