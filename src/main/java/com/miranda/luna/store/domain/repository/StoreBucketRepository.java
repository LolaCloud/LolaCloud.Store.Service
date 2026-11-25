package com.miranda.luna.store.domain.repository;

import com.miranda.luna.store.domain.model.StoreBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreBucketRepository extends JpaRepository<StoreBucket, UUID> {
    Optional<StoreBucket> findByName(String name);
    boolean existsByName(String name);
}
