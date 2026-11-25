package com.miranda.luna.store.infrastructure.web.dto;

public record StoreStatusResponse(
        String status,
        String service,
        String version,
        Integer totalBuckets,
        Long totalObjects,
        Long totalSizeBytes,
        Boolean s3Connected
) {
}
