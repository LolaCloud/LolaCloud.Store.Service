package com.miranda.luna.store.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateUploadUrlRequest(
        @NotBlank(message = "Object key is required")
        String objectKey,
        String contentType,
        Long durationMinutes,
        String publicEndpoint
) {
    public GenerateUploadUrlRequest(String objectKey, String contentType, Long durationMinutes) {
        this(objectKey, contentType, durationMinutes, null);
    }
}
