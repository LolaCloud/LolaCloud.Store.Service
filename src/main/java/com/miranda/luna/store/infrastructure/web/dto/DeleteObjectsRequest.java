package com.miranda.luna.store.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeleteObjectsRequest(
        @NotEmpty(message = "Object keys list cannot be empty")
        List<String> objectKeys
) {
}
