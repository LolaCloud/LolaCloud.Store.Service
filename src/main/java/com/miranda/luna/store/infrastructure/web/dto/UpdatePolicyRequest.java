package com.miranda.luna.store.infrastructure.web.dto;

import com.miranda.luna.store.domain.model.BucketAccessPolicy;
import jakarta.validation.constraints.NotNull;

public record UpdatePolicyRequest(
        @NotNull(message = "Access policy is required")
        BucketAccessPolicy accessPolicy
) {
}
