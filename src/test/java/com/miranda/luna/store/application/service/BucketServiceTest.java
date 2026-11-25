package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.exception.ConflictException;
import com.miranda.luna.store.domain.exception.InvalidOperationException;
import com.miranda.luna.store.domain.exception.ResourceNotFoundException;
import com.miranda.luna.store.domain.model.BucketAccessPolicy;
import com.miranda.luna.store.domain.model.StoreBucket;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.BucketResponse;
import com.miranda.luna.store.infrastructure.web.dto.CreateBucketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    @Mock
    private StoreBucketRepository bucketRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private BucketService bucketService;

    @Test
    void testValidateBucketNameValid() {
        assertDoesNotThrow(() -> BucketService.validateBucketName("valid-bucket-name"));
        assertDoesNotThrow(() -> BucketService.validateBucketName("123-bucket"));
        assertDoesNotThrow(() -> BucketService.validateBucketName("bucket.name.with.dots"));
        assertDoesNotThrow(() -> BucketService.validateBucketName("a".repeat(63)));
        assertDoesNotThrow(() -> BucketService.validateBucketName("abc"));
    }

    @Test
    void testValidateBucketNameInvalid() {
        // Too short (< 3)
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("ab"));
        // Too long (> 63)
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("a".repeat(64)));
        // Uppercase
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("MyBucket"));
        // Consecutive dots
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("my..bucket"));
        // Dash dot combinations
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("my.-bucket"));
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("my-.bucket"));
        // IP address
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("192.168.1.1"));
        // Start or end with non-alphanumeric
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("-mybucket"));
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("mybucket-"));
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName(".mybucket"));
        assertThrows(InvalidOperationException.class, () -> BucketService.validateBucketName("mybucket."));
    }

    @Test
    void testCreateBucketConflictThrows() {
        CreateBucketRequest req = new CreateBucketRequest("existing-bucket", "us-east-1", BucketAccessPolicy.PRIVATE, false);
        when(bucketRepository.existsByName("existing-bucket")).thenReturn(true);

        assertThrows(ConflictException.class, () -> bucketService.createBucket(req));
    }

    @Test
    void testGetBucketNotFoundThrows() {
        when(bucketRepository.findByName("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bucketService.getBucket("non-existent"));
    }

    @Test
    void testDeleteBucketNotFoundThrows() {
        when(bucketRepository.findByName("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bucketService.deleteBucket("non-existent", false));
    }

    @Test
    void testUpdateAccessPolicyNotFoundThrows() {
        when(bucketRepository.findByName("non-existent")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bucketService.updateAccessPolicy("non-existent", BucketAccessPolicy.PUBLIC_READ));
    }

    @Test
    void testListBucketsEnrichment() {
        StoreBucket bucket = StoreBucket.builder()
                .id(UUID.randomUUID())
                .name("test-bucket")
                .region("lola-local")
                .status("ACTIVE")
                .accessPolicy(BucketAccessPolicy.PRIVATE)
                .versioningEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bucketRepository.findAll()).thenReturn(List.of(bucket));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().keyCount(5).build()
        );

        List<BucketResponse> responses = bucketService.listBuckets();
        assertEquals(1, responses.size());
        assertEquals("test-bucket", responses.get(0).name());
        assertEquals(5L, responses.get(0).objectCount());
        assertFalse(responses.get(0).isProtected());
    }

    @Test
    void testDeleteBucketProtectedFlagThrows() {
        StoreBucket bucket = StoreBucket.builder()
                .id(UUID.randomUUID())
                .name("custom-data")
                .isProtected(true)
                .build();
        when(bucketRepository.findByName("custom-data")).thenReturn(Optional.of(bucket));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> bucketService.deleteBucket("custom-data", false));
        assertTrue(ex.getMessage().contains("is protected and cannot be deleted"));

        // Even with force=true, deletion must be blocked
        assertThrows(InvalidOperationException.class,
                () -> bucketService.deleteBucket("custom-data", true));

        verify(bucketRepository, never()).delete(any());
        verify(s3Client, never()).deleteBucket(any(software.amazon.awssdk.services.s3.model.DeleteBucketRequest.class));
    }

    @Test
    void testDeleteBucketProtectedNameSuffixThrows() {
        StoreBucket bucket = StoreBucket.builder()
                .id(UUID.randomUUID())
                .name("relate-backups")
                .isProtected(false)
                .build();
        when(bucketRepository.findByName("relate-backups")).thenReturn(Optional.of(bucket));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> bucketService.deleteBucket("relate-backups", false));
        assertTrue(ex.getMessage().contains("is protected and cannot be deleted"));
        verify(bucketRepository, never()).delete(any());
    }

    @Test
    void testDeleteBucketLolaBackupPatternThrows() {
        StoreBucket bucket = StoreBucket.builder()
                .id(UUID.randomUUID())
                .name("lola-relate-backups")
                .isProtected(false)
                .build();
        when(bucketRepository.findByName("lola-relate-backups")).thenReturn(Optional.of(bucket));

        InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                () -> bucketService.deleteBucket("lola-relate-backups", true));
        assertTrue(ex.getMessage().contains("is protected and cannot be deleted"));
        verify(bucketRepository, never()).delete(any());
    }

    @Test
    void testIsProtectedBucketName() {
        assertTrue(BucketService.isProtectedBucketName("lola-relate-backups"));
        assertTrue(BucketService.isProtectedBucketName("cluster-backups"));
        assertTrue(BucketService.isProtectedBucketName("lola-my-backup-bucket"));
        assertFalse(BucketService.isProtectedBucketName("lola-test-bucket"));
        assertFalse(BucketService.isProtectedBucketName("my-bucket"));
        assertFalse(BucketService.isProtectedBucketName(null));
    }

    @Test
    void testCreateBucketAutoProtectsBackupBucketName() {
        CreateBucketRequest req = new CreateBucketRequest("user-backups", "us-east-1", BucketAccessPolicy.PRIVATE, false);
        when(bucketRepository.existsByName("user-backups")).thenReturn(false);

        ArgumentCaptor<StoreBucket> captor = ArgumentCaptor.forClass(StoreBucket.class);
        when(bucketRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        BucketResponse resp = bucketService.createBucket(req);

        assertTrue(captor.getValue().getIsProtected());
        assertTrue(resp.isProtected());
    }
}
