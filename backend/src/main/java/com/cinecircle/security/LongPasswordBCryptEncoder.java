package com.cinecircle.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * BCrypt only uses the first 72 bytes of input (and Spring Security 6.3+ rejects longer input with an
 * exception). The contract allows passwords of up to 100 characters (possibly multi-byte), so inputs over
 * 72 UTF-8 bytes are first reduced to Base64(SHA-256(password)) (44 bytes) and then BCrypt-hashed.
 * Shorter passwords are hashed unchanged, so standard BCrypt hashes remain valid.
 */
public class LongPasswordBCryptEncoder implements PasswordEncoder {

    private static final int BCRYPT_MAX_BYTES = 72;
    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(normalize(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(normalize(rawPassword), encodedPassword);
    }

    static CharSequence normalize(CharSequence raw) {
        if (raw == null) {
            return null;
        }
        byte[] bytes = raw.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= BCRYPT_MAX_BYTES) {
            return raw;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
