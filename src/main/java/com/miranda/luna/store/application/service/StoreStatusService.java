package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.model.StoreBucket;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.StoreStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

@Service
public class StoreStatusService {

    private static final Logger log = LoggerFactory.getLogger(StoreStatusService.class);

    private final StoreBucketRepository bucketRepository;
    private final S3Client s3Client;

    public StoreStatusService(StoreBucketRepository bucketRepository, S3Client s3Client) {
        this.bucketRepository = bucketRepository;
        this.s3Client = s3Client;
    }

    public StoreStatusResponse getStatusSummary() {
        boolean s3Connected = false;
        long totalObjects = 0L;
        long totalSizeBytes = 0L;
        long bucketCount = bucketRepository.count();

        try {
            s3Client.listBuckets();
            s3Connected = true;

            List<StoreBucket> buckets = bucketRepository.findAll();
            for (StoreBucket bucket : buckets) {
                try {
                    ListObjectsV2Response res = s3Client.listObjectsV2(
                            ListObjectsV2Request.builder().bucket(bucket.getName()).build()
                    );
                    long count = res.keyCount() != null ? res.keyCount() : (res.contents() != null ? res.contents().size() : 0);
                    totalObjects += count;
                    if (res.contents() != null) {
                        for (S3Object obj : res.contents()) {
                            totalSizeBytes += obj.size() != null ? obj.size() : 0L;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not read stats for bucket {}: {}", bucket.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("S3 connectivity check failed: {}", e.getMessage());
        }

        String status = s3Connected ? "UP" : "DEGRADED";
        return new StoreStatusResponse(
                status,
                "store",
                "0.0.1-SNAPSHOT",
                (int) bucketCount,
                totalObjects,
                totalSizeBytes,
                s3Connected
        );
    }
}
