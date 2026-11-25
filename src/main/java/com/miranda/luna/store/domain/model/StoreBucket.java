package com.miranda.luna.store.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_buckets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreBucket {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 63)
    private String name;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Enumerated(EnumType.STRING)
    @Column(name = "access_policy", nullable = false, length = 50)
    @Builder.Default
    private BucketAccessPolicy accessPolicy = BucketAccessPolicy.PRIVATE;

    @Column(name = "versioning_enabled", nullable = false)
    @Builder.Default
    private Boolean versioningEnabled = false;

    @Column(name = "is_protected", nullable = false)
    @Builder.Default
    private Boolean isProtected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (region == null || region.isBlank()) {
            region = "lola-local";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
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
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getLrn() {
        return "lola:store:bucket:" + name;
    }
}
