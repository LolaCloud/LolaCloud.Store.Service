package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.exception.ConflictException;
import com.miranda.luna.store.domain.exception.InvalidOperationException;
import com.miranda.luna.store.domain.exception.ResourceNotFoundException;
import com.miranda.luna.store.domain.model.BucketAccessPolicy;
import com.miranda.luna.store.domain.model.StoreBucket;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.BucketResponse;
import com.miranda.luna.store.infrastructure.web.dto.CreateBucketRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

import java.util.ArrayList;
import java.util.List;

@Service
public class BucketService {

    private static final Logger log = LoggerFactory.getLogger(BucketService.class);

    private final StoreBucketRepository bucketRepository;
    private final S3Client s3Client;

    public BucketService(StoreBucketRepository bucketRepository, S3Client s3Client) {
        this.bucketRepository = bucketRepository;
        this.s3Client = s3Client;
    }

    @Transactional(readOnly = true)
    public List<BucketResponse> listBuckets() {
        List<StoreBucket> buckets = bucketRepository.findAll();
        return buckets.stream()
                .map(this::enrichBucketResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BucketResponse getBucket(String name) {
        StoreBucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));
        return enrichBucketResponse(bucket);
    }

    @Transactional
    public BucketResponse createBucket(CreateBucketRequest request) {
        validateBucketName(request.name());

        if (bucketRepository.existsByName(request.name())) {
            throw new ConflictException("Bucket already exists: " + request.name());
        }

        try {
            s3Client.createBucket(software.amazon.awssdk.services.s3.model.CreateBucketRequest.builder()
                    .bucket(request.name())
                    .build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.warn("Bucket {} already exists in S3: {}", request.name(), e.getMessage());
        } catch (S3Exception e) {
            log.error("Failed creating bucket {} in S3: {}", request.name(), e.getMessage(), e);
            throw new InvalidOperationException("Failed creating S3 bucket: " + e.awsErrorDetails().errorMessage());
        }

        if (request.accessPolicy() == BucketAccessPolicy.PUBLIC_READ) {
            applyPublicReadPolicy(request.name());
        }

        if (Boolean.TRUE.equals(request.versioningEnabled())) {
            enableVersioning(request.name());
        }

        StoreBucket bucket = StoreBucket.builder()
                .name(request.name())
                .region(request.region() != null && !request.region().isBlank() ? request.region() : "lola-local")
                .status("ACTIVE")
                .accessPolicy(request.accessPolicy() != null ? request.accessPolicy() : BucketAccessPolicy.PRIVATE)
                .versioningEnabled(Boolean.TRUE.equals(request.versioningEnabled()))
                .isProtected(Boolean.TRUE.equals(request.isProtected()) || isProtectedBucketName(request.name()))
                .build();

        StoreBucket saved = bucketRepository.save(bucket);
        return enrichBucketResponse(saved);
    }

    @Transactional
    public void deleteBucket(String name, boolean force) {
        StoreBucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));

        if (isProtectedBucket(bucket)) {
            throw new InvalidOperationException("Bucket '" + name + "' is protected and cannot be deleted because it is reserved for system backups.");
        }

        if (force) {
            purgeBucketObjects(name);
        }

        try {
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(name).build());
        } catch (S3Exception e) {
            if ("BucketNotEmpty".equals(e.awsErrorDetails().errorCode())) {
                throw new InvalidOperationException("Bucket is not empty. Use force=true to delete bucket and all its objects.");
            }
            log.warn("S3 bucket deletion error for {}: {}", name, e.getMessage());
        }

        bucketRepository.delete(bucket);
    }

    @Transactional
    public BucketResponse updateAccessPolicy(String name, BucketAccessPolicy policy) {
        StoreBucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));

        if (policy == BucketAccessPolicy.PUBLIC_READ) {
            applyPublicReadPolicy(name);
        } else {
            removePublicReadPolicy(name);
        }

        bucket.setAccessPolicy(policy);
        StoreBucket updated = bucketRepository.save(bucket);
        return enrichBucketResponse(updated);
    }

    private void applyPublicReadPolicy(String bucketName) {
        String policyJson = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Sid": "PublicReadGetObject",
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": "arn:aws:s3:::%s/*"
                    }
                  ]
                }
                """.formatted(bucketName);

        try {
            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policyJson)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to apply public-read policy to bucket {}: {}", bucketName, e.getMessage());
        }
    }

    private void removePublicReadPolicy(String bucketName) {
        try {
            s3Client.deleteBucketPolicy(DeleteBucketPolicyRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.debug("No bucket policy to remove or error removing for {}: {}", bucketName, e.getMessage());
        }
    }

    private void enableVersioning(String bucketName) {
        try {
            s3Client.putBucketVersioning(PutBucketVersioningRequest.builder()
                    .bucket(bucketName)
                    .versioningConfiguration(VersioningConfiguration.builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build());
        } catch (Exception e) {
            log.warn("Failed enabling versioning for bucket {}: {}", bucketName, e.getMessage());
        }
    }

    private void purgeBucketObjects(String bucketName) {
        try {
            ListObjectsV2Response objects = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build());
            if (!objects.contents().isEmpty()) {
                List<ObjectIdentifier> identifiers = objects.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .toList();
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(identifiers).build())
                        .build());
            }

            try {
                ListObjectVersionsResponse versions = s3Client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).build());
                List<ObjectIdentifier> versionIds = new ArrayList<>();
                if (versions.versions() != null) {
                    for (ObjectVersion v : versions.versions()) {
                        versionIds.add(ObjectIdentifier.builder().key(v.key()).versionId(v.versionId()).build());
                    }
                }
                if (versions.deleteMarkers() != null) {
                    for (DeleteMarkerEntry dm : versions.deleteMarkers()) {
                        versionIds.add(ObjectIdentifier.builder().key(dm.key()).versionId(dm.versionId()).build());
                    }
                }
                if (!versionIds.isEmpty()) {
                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucketName)
                            .delete(Delete.builder().objects(versionIds).build())
                            .build());
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            log.warn("Failed purging objects from bucket {}: {}", bucketName, e.getMessage());
        }
    }

    public BucketResponse enrichBucketResponse(StoreBucket bucket) {
        long count = 0L;
        long totalSize = 0L;
        try {
            ListObjectsV2Response res = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket.getName()).build());
            count = res.keyCount() != null ? res.keyCount() : res.contents().size();
            for (S3Object obj : res.contents()) {
                totalSize += obj.size() != null ? obj.size() : 0L;
            }
        } catch (Exception e) {
            log.debug("Could not fetch S3 stats for bucket {}: {}", bucket.getName(), e.getMessage());
        }

        return new BucketResponse(
                bucket.getId(),
                bucket.getLrn(),
                bucket.getName(),
                bucket.getRegion(),
                bucket.getStatus(),
                bucket.getAccessPolicy() != null ? bucket.getAccessPolicy().name() : "PRIVATE",
                bucket.getVersioningEnabled(),
                bucket.getIsProtected() != null ? bucket.getIsProtected() : isProtectedBucketName(bucket.getName()),
                count,
                totalSize,
                bucket.getCreatedAt()
        );
    }

    public boolean isProtectedBucket(StoreBucket bucket) {
        if (bucket == null) return false;
        if (Boolean.TRUE.equals(bucket.getIsProtected())) return true;
        return isProtectedBucketName(bucket.getName());
    }

    public static boolean isProtectedBucketName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase().trim();
        return lower.equals("lola-relate-backups")
                || lower.endsWith("-backups")
                || (lower.startsWith("lola-") && lower.contains("backup"));
    }

    public static void validateBucketName(String name) {
        if (name == null || name.length() < 3 || name.length() > 63) {
            throw new InvalidOperationException("Bucket name must be between 3 and 63 characters long");
        }
        if (!name.matches("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) {
            throw new InvalidOperationException("Bucket name must consist of lowercase letters, numbers, periods (.), and hyphens (-), and start and end with a letter or number");
        }
        if (name.contains("..") || name.contains(".-") || name.contains("-.")) {
            throw new InvalidOperationException("Bucket name cannot contain consecutive periods or dash-period combinations");
        }
        if (name.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            throw new InvalidOperationException("Bucket name cannot be formatted as an IP address");
        }
    }
}
