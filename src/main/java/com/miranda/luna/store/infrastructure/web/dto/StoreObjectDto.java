package com.miranda.luna.store.infrastructure.web.dto;

import java.time.Instant;

public record StoreObjectDto(
        String key,
        String name,
        Long size,
        Instant lastModified,
        String eTag,
        String contentType,
        Boolean isFolder
) {
}
