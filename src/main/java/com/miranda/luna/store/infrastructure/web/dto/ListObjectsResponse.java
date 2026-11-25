package com.miranda.luna.store.infrastructure.web.dto;

import java.util.List;

public record ListObjectsResponse(
        String bucketName,
        String prefix,
        String delimiter,
        List<StoreObjectDto> objects,
        List<String> commonPrefixes
) {
}
