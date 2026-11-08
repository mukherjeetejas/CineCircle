package com.cinecircle.user;

import com.cinecircle.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read helpers shared by services: lookups that throw NOT_FOUND and batched (single-query) loads. */
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final UserRepository users;

    public User byUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        return users.findByUsername(normalized)
                .orElseThrow(() -> ApiException.notFound("User not found: " + username));
    }

    public User byId(String id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    /** One {@code $in} query; returns id -> user. */
    public Map<String, User> byIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
