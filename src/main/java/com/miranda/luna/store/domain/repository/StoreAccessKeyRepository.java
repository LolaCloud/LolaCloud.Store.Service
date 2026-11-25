package com.miranda.luna.store.domain.repository;

import com.miranda.luna.store.domain.model.StoreAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreAccessKeyRepository extends JpaRepository<StoreAccessKey, UUID> {
    Optional<StoreAccessKey> findByAccessKey(String accessKey);
    List<StoreAccessKey> findByBucketId(UUID bucketId);
}
