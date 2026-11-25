package com.miranda.luna.store.infrastructure.web.dto;

import java.time.Instant;
import java.util.UUID;

public record BucketResponse(
        UUID id,
        String lrn,
        String name,
        String region,
        String status,
        String accessPolicy,
        Boolean versioningEnabled,
        Boolean isProtected,
        Long objectCount,
        Long totalSizeBytes,
        Instant createdAt
) {
}
