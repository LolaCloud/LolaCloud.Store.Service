package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.exception.InvalidOperationException;
import com.miranda.luna.store.domain.exception.ResourceNotFoundException;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectServiceTest {

    @Mock
    private StoreBucketRepository bucketRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private ObjectService objectService;

    @BeforeEach
    void setUp() {
        objectService = new ObjectService(bucketRepository, s3Client, s3Presigner, 60L);
    }

    @Test
    void testBucketNotFoundThrowsForOperations() {
        when(bucketRepository.existsByName("missing-bucket")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> objectService.listObjects("missing-bucket", null, null));
        assertThrows(ResourceNotFoundException.class, () -> objectService.createFolder("missing-bucket", "folder/"));
        assertThrows(ResourceNotFoundException.class, () -> objectService.generateUploadPresignedUrl("missing-bucket", "key.txt", null, null));
        assertThrows(ResourceNotFoundException.class, () -> objectService.generateDownloadPresignedUrl("missing-bucket", "key.txt", null));
        assertThrows(ResourceNotFoundException.class, () -> objectService.deleteObject("missing-bucket", "key.txt"));
    }

    @Test
    void testInvalidFolderThrows() {
        when(bucketRepository.existsByName("valid-bucket")).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> objectService.createFolder("valid-bucket", ""));
        assertThrows(InvalidOperationException.class, () -> objectService.createFolder("valid-bucket", "   "));
        assertThrows(InvalidOperationException.class, () -> objectService.createFolder("valid-bucket", "/"));
    }

    @Test
    void testEmptyKeyThrows() {
        when(bucketRepository.existsByName("valid-bucket")).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> objectService.generateUploadPresignedUrl("valid-bucket", "", null, null));
        assertThrows(InvalidOperationException.class, () -> objectService.generateDownloadPresignedUrl("valid-bucket", "", null));
    }

    @Test
    void testDeleteNonEmptyFolderThrowsInvalidOperationException() {
        when(bucketRepository.existsByName("valid-bucket")).thenReturn(true);

        software.amazon.awssdk.services.s3.model.S3Object folderMarker =
                software.amazon.awssdk.services.s3.model.S3Object.builder().key("pasta/").build();
        software.amazon.awssdk.services.s3.model.S3Object childFile =
                software.amazon.awssdk.services.s3.model.S3Object.builder().key("pasta/arquivo.txt").build();

        software.amazon.awssdk.services.s3.model.ListObjectsV2Response listRes =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Response.builder()
                        .contents(java.util.List.of(folderMarker, childFile))
                        .build();

        when(s3Client.listObjectsV2(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .thenReturn(listRes);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                objectService.deleteObject("valid-bucket", "pasta/")
        );

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("não pode ser excluída"));
    }

    @Test
    void testDeleteEmptyFolderSucceeds() {
        when(bucketRepository.existsByName("valid-bucket")).thenReturn(true);

        software.amazon.awssdk.services.s3.model.S3Object folderMarker =
                software.amazon.awssdk.services.s3.model.S3Object.builder().key("pasta-vazia/").build();

        software.amazon.awssdk.services.s3.model.ListObjectsV2Response listRes =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Response.builder()
                        .contents(java.util.List.of(folderMarker))
                        .build();

        when(s3Client.listObjectsV2(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .thenReturn(listRes);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                objectService.deleteObject("valid-bucket", "pasta-vazia/")
        );
    }
}
