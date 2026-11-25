package com.miranda.luna.store.infrastructure.web.dto;

import java.time.Instant;

public record PresignedUrlResponse(
        String url,
        String method,
        Instant expiresAt,
        String objectKey,
        String bucketName
) {
}
