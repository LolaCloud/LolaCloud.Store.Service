package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.model.StoreBucket;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.StoreStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreStatusServiceTest {

    @Mock
    private StoreBucketRepository bucketRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StoreStatusService storeStatusService;

    @Test
    void testGetStatusSummaryWhenS3IsConnected() {
        StoreBucket bucket = StoreBucket.builder().name("alpha").build();
        when(bucketRepository.count()).thenReturn(1L);
        when(bucketRepository.findAll()).thenReturn(List.of(bucket));
        when(s3Client.listBuckets()).thenReturn(ListBucketsResponse.builder().build());

        S3Object s3Object = S3Object.builder().key("file1.txt").size(1024L).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .keyCount(1)
                        .contents(List.of(s3Object))
                        .build()
        );

        StoreStatusResponse response = storeStatusService.getStatusSummary();
        assertEquals("UP", response.status());
        assertEquals("store", response.service());
        assertEquals(1, response.totalBuckets());
        assertEquals(1L, response.totalObjects());
        assertEquals(1024L, response.totalSizeBytes());
        assertTrue(response.s3Connected());
    }

    @Test
    void testGetStatusSummaryWhenS3Fails() {
        when(bucketRepository.count()).thenReturn(2L);
        when(s3Client.listBuckets()).thenThrow(S3Exception.builder().message("Connection refused").build());

        StoreStatusResponse response = storeStatusService.getStatusSummary();
        assertEquals("DEGRADED", response.status());
        assertFalse(response.s3Connected());
        assertEquals(2, response.totalBuckets());
    }
}
