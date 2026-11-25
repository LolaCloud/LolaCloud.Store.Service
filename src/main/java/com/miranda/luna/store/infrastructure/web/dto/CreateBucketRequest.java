package com.miranda.luna.store.infrastructure.web.dto;

import com.miranda.luna.store.domain.model.BucketAccessPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBucketRequest(
        @NotBlank(message = "Bucket name is required")
        @Size(min = 3, max = 63, message = "Bucket name must be between 3 and 63 characters")
        @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", message = "Bucket name must follow RFC 1123 DNS conventions")
        String name,

        String region,

        BucketAccessPolicy accessPolicy,

        Boolean versioningEnabled,

        Boolean isProtected
) {
    public CreateBucketRequest {
        if (region == null || region.isBlank()) {
            region = "lola-local";
        }
        if (accessPolicy == null) {
            accessPolicy = BucketAccessPolicy.PRIVATE;
        }
        if (versioningEnabled == null) {
            versioningEnabled = false;
        }
        if (isProtected == null) {
            isProtected = false;
        }
    }

    public CreateBucketRequest(String name, String region, BucketAccessPolicy accessPolicy, Boolean versioningEnabled) {
        this(name, region, accessPolicy, versioningEnabled, false);
    }
}
