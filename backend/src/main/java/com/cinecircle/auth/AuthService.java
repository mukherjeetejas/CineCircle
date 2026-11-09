package com.cinecircle.auth;

import com.cinecircle.auth.dto.AuthResponse;
import com.cinecircle.auth.dto.LoginRequest;
import com.cinecircle.auth.dto.RegisterRequest;
import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.common.RateLimiter;
import com.cinecircle.follow.FollowStatus;
import com.cinecircle.security.JwtService;
import com.cinecircle.user.User;
import com.cinecircle.user.UserMapper;
import com.cinecircle.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Would collide with literal routes under /api/users. */
    static final Set<String> RESERVED_USERNAMES = Set.of("me", "search", "suggestions");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final RateLimiter rateLimiter;

    /** Registration limited per client IP per hour; failed registrations give their slot back. */
    public AuthResponse register(RegisterRequest req, String clientIp) {
        rateLimiter.acquireRegister(clientIp);
        try {
            return register(req);
        } catch (RuntimeException e) {
            rateLimiter.releaseRegister(clientIp);
            throw e;
        }
    }

    /** Every attempt counts towards the per-IP per-minute login limit. */
    public AuthResponse login(LoginRequest req, String clientIp) {
        rateLimiter.acquireLogin(clientIp);
        return login(req);
    }

    AuthResponse register(RegisterRequest req) {
        if (RESERVED_USERNAMES.contains(req.username())) {
            throw ApiException.validation("username", "This username is reserved");
        }
        if (users.existsByUsername(req.username())) {
            throw usernameTaken();
        }
        if (users.existsByEmail(req.email())) {
            throw emailTaken();
        }
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .displayName(req.displayName())
                .privateAccount(false)
                .createdAt(Instant.now())
                .build();
        try {
            user = users.insert(user);
        } catch (DuplicateKeyException e) {
            // lost a race against a concurrent registration
            throw users.existsByUsername(req.username()) ? usernameTaken() : emailTaken();
        }
        return toResponse(user);
    }

    AuthResponse login(LoginRequest req) {
        String id = req.usernameOrEmail().trim().toLowerCase();
        Optional<User> user = id.contains("@") ? users.findByEmail(id) : users.findByUsername(id);
        if (user.isEmpty() || !passwordEncoder.matches(req.password(), user.get().getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid username/email or password");
        }
        return toResponse(user.get());
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwtService.generate(u.getId(), u.getUsername()),
                userMapper.toProfile(u, FollowStatus.SELF, true));
    }

    private static ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, "Username is already taken",
                Map.of("username", "is already taken"));
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.EMAIL_TAKEN, "Email is already registered",
                Map.of("email", "is already registered"));
    }
}
