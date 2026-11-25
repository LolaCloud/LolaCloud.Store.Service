package com.miranda.luna.store.application.service;

import com.miranda.luna.store.domain.exception.InvalidOperationException;
import com.miranda.luna.store.domain.exception.ResourceNotFoundException;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.ListObjectsResponse;
import com.miranda.luna.store.infrastructure.web.dto.PresignedUrlResponse;
import com.miranda.luna.store.infrastructure.web.dto.StoreObjectDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;

@Service
public class ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectService.class);

    private final StoreBucketRepository bucketRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final long defaultExpirationMinutes;

    @Value("${lola.store.s3.endpoint:http://127.0.0.1:9000}")
    private String defaultEndpoint = "http://127.0.0.1:9000";

    @Value("${lola.store.s3.region:us-east-1}")
    private String region = "us-east-1";

    @Value("${lola.store.s3.access-key:lola_admin}")
    private String accessKey = "lola_admin";

    @Value("${lola.store.s3.secret-key:lola_admin_secret}")
    private String secretKey = "lola_admin_secret";

    public ObjectService(
            StoreBucketRepository bucketRepository,
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${lola.store.s3.presigned-url-expiration-minutes:60}") long defaultExpirationMinutes
    ) {
        this.bucketRepository = bucketRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.defaultExpirationMinutes = defaultExpirationMinutes;
    }

    public ListObjectsResponse listObjects(String bucketName, String prefix, String delimiter) {
        ensureBucketExists(bucketName);

        String effectivePrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim() : "";
        String effectiveDelimiter = (delimiter != null && !delimiter.isBlank()) ? delimiter : "/";

        ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .delimiter(effectiveDelimiter);

        if (!effectivePrefix.isBlank()) {
            reqBuilder.prefix(effectivePrefix);
        }

        ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());

        final String normPrefix = effectivePrefix.isEmpty()
                ? ""
                : (effectivePrefix.endsWith("/") ? effectivePrefix : effectivePrefix + "/");

        List<StoreObjectDto> objects = new ArrayList<>();
        if (response.contents() != null) {
            objects = response.contents().stream()
                    .filter(s3Obj -> {
                        String key = s3Obj.key();
                        // Filter out the directory marker for the current folder itself
                        if (!normPrefix.isEmpty()) {
                            if (key.equals(normPrefix) || (key + "/").equals(normPrefix)) {
                                return false;
                            }
                        }
                        return !key.isEmpty() && !"/".equals(key);
                    })
                    .map(s3Obj -> {
                        String key = s3Obj.key();
                        boolean isFolder = key.endsWith("/");
                        String name = extractNameFromKey(key);
                        String eTag = s3Obj.eTag() != null ? s3Obj.eTag().replace("\"", "") : null;
                        String contentType = isFolder ? "application/x-directory" : guessContentType(key);

                        return new StoreObjectDto(
                                key,
                                name,
                                s3Obj.size(),
                                s3Obj.lastModified(),
                                eTag,
                                contentType,
                                isFolder
                        );
                    }).toList();
        }

        List<String> commonPrefixes = new ArrayList<>();
        if (response.commonPrefixes() != null) {
            commonPrefixes = response.commonPrefixes().stream()
                    .map(CommonPrefix::prefix)
                    .filter(cp -> !cp.equals(normPrefix))
                    .toList();
        }

        return new ListObjectsResponse(bucketName, effectivePrefix, effectiveDelimiter, objects, commonPrefixes);
    }

    public StoreObjectDto createFolder(String bucketName, String folderPath) {
        ensureBucketExists(bucketName);

        if (folderPath == null || folderPath.isBlank()) {
            throw new InvalidOperationException("Folder path cannot be empty");
        }

        String normalizedPath = folderPath.trim();
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }

        if (normalizedPath.isBlank() || "/".equals(normalizedPath)) {
            throw new InvalidOperationException("Invalid folder path");
        }

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(normalizedPath)
                        .contentType("application/x-directory")
                        .contentLength(0L)
                        .build(),
                RequestBody.empty()
        );

        String folderName = extractNameFromKey(normalizedPath);
        return new StoreObjectDto(
                normalizedPath,
                folderName,
                0L,
                Instant.now(),
                null,
                "application/x-directory",
                true
        );
    }

    public PresignedUrlResponse generateUploadPresignedUrl(
            String bucketName,
            String objectKey,
            String contentType,
            Long durationMinutes
    ) {
        return generateUploadPresignedUrl(bucketName, objectKey, contentType, durationMinutes, null);
    }

    public PresignedUrlResponse generateUploadPresignedUrl(
            String bucketName,
            String objectKey,
            String contentType,
            Long durationMinutes,
            String customEndpoint
    ) {
        ensureBucketExists(bucketName);

        String cleanKey = sanitizeObjectKey(objectKey);
        long minutes = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : defaultExpirationMinutes;

        // NOTE: We deliberately do NOT bind contentType to PutObjectRequest here.
        // Signing the Content-Type header in SigV4 causes browser uploads to fail with 403
        // (SignatureDoesNotMatch) whenever browsers alter/append charset (e.g. text/x-log; charset=utf-8).
        // Without contentType in SignedHeaders, S3 accepts any browser Content-Type and stores it correctly.
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(cleanKey)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .putObjectRequest(putReq)
                .build();

        S3Presigner presigner = resolvePresigner(customEndpoint);
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignedUrlResponse(
                presigned.url().toString(),
                "PUT",
                presigned.expiration(),
                cleanKey,
                bucketName
        );
    }

    public PresignedUrlResponse generateDownloadPresignedUrl(
            String bucketName,
            String objectKey,
            Long durationMinutes
    ) {
        return generateDownloadPresignedUrl(bucketName, objectKey, durationMinutes, null);
    }

    public PresignedUrlResponse generateDownloadPresignedUrl(
            String bucketName,
            String objectKey,
            Long durationMinutes,
            String customEndpoint
    ) {
        ensureBucketExists(bucketName);

        String cleanKey = sanitizeObjectKey(objectKey);
        long minutes = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : defaultExpirationMinutes;

        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(cleanKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .getObjectRequest(getReq)
                .build();

        S3Presigner presigner = resolvePresigner(customEndpoint);
        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return new PresignedUrlResponse(
                presigned.url().toString(),
                "GET",
                presigned.expiration(),
                cleanKey,
                bucketName
        );
    }

    private S3Presigner resolvePresigner(String customEndpoint) {
        if (customEndpoint == null || customEndpoint.isBlank() || customEndpoint.equals(defaultEndpoint)) {
            return s3Presigner;
        }
        try {
            return S3Presigner.builder()
                    .endpointOverride(URI.create(customEndpoint.trim()))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        } catch (Exception e) {
            log.warn("Failed creating custom S3Presigner for endpoint {}: {}. Falling back to default.", customEndpoint, e.getMessage());
            return s3Presigner;
        }
    }

    public void deleteObject(String bucketName, String objectKey) {
        ensureBucketExists(bucketName);
        String cleanKey = sanitizeObjectKey(objectKey);

        validateNotNonEmptyFolder(bucketName, cleanKey);

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(cleanKey)
                .build());

        if (!cleanKey.endsWith("/")) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(cleanKey + "/")
                        .build());
            } catch (Exception ignored) {
            }
        }
    }

    public void deleteObjects(String bucketName, List<String> objectKeys) {
        ensureBucketExists(bucketName);

        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }

        List<String> sanitizedKeys = objectKeys.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(this::sanitizeObjectKey)
                .toList();

        if (sanitizedKeys.isEmpty()) {
            return;
        }

        for (String key : sanitizedKeys) {
            validateNotNonEmptyFolder(bucketName, key);
        }

        List<ObjectIdentifier> identifiers = new ArrayList<>();
        for (String key : sanitizedKeys) {
            identifiers.add(ObjectIdentifier.builder().key(key).build());
            if (!key.endsWith("/")) {
                identifiers.add(ObjectIdentifier.builder().key(key + "/").build());
            }
        }

        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(identifiers).build())
                .build());
    }

    private void validateNotNonEmptyFolder(String bucketName, String key) {
        String folderPrefix = key.endsWith("/") ? key : key + "/";

        ListObjectsV2Response res = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPrefix)
                .maxKeys(10)
                .build());

        if (res.contents() != null && !res.contents().isEmpty()) {
            boolean hasChildren = res.contents().stream()
                    .anyMatch(obj -> !obj.key().equals(folderPrefix));

            if (hasChildren) {
                String folderName = extractNameFromKey(folderPrefix);
                throw new InvalidOperationException("A pasta '" + folderName + "' não pode ser excluída porque contém arquivos ou subpastas. Remova todo o conteúdo antes de excluí-la.");
            }
        }
    }

    private void ensureBucketExists(String bucketName) {
        if (!bucketRepository.existsByName(bucketName)) {
            throw new ResourceNotFoundException("Bucket not found: " + bucketName);
        }
    }

    private String sanitizeObjectKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidOperationException("Object key cannot be empty");
        }
        String sanitized = key.trim();
        while (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.isBlank()) {
            throw new InvalidOperationException("Object key cannot be empty");
        }
        return sanitized;
    }

    private String extractNameFromKey(String key) {
        if (key.endsWith("/")) {
            String trimmed = key.substring(0, key.length() - 1);
            int lastSlash = trimmed.lastIndexOf('/');
            return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        }
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    private String guessContentType(String key) {
        if (key == null) return "application/octet-stream";
        if (key.endsWith("/")) return "application/x-directory";
        String guessed = URLConnection.guessContentTypeFromName(key);
        if (guessed != null) return guessed;
        if (key.endsWith(".json")) return "application/json";
        if (key.endsWith(".txt") || key.endsWith(".md") || key.endsWith(".log")) return "text/plain";
        if (key.endsWith(".csv")) return "text/csv";
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return "image/jpeg";
        if (key.endsWith(".gif")) return "image/gif";
        if (key.endsWith(".svg")) return "image/svg+xml";
        if (key.endsWith(".pdf")) return "application/pdf";
        if (key.endsWith(".zip")) return "application/zip";
        if (key.endsWith(".tar") || key.endsWith(".gz")) return "application/gzip";
        return "application/octet-stream";
    }
}
