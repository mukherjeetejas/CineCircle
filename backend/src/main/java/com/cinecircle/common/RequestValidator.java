package com.cinecircle.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Programmatic Bean Validation, used where the payload is parsed manually (e.g. multipart JSON parts). */
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;

    public <T> T validate(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            violations.forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Validation failed", fields);
        }
        return target;
    }
}
