package com.miranda.luna.store.infrastructure.web.controller;

import com.miranda.luna.store.application.service.ObjectService;
import com.miranda.luna.store.infrastructure.web.dto.CreateFolderRequest;
import com.miranda.luna.store.infrastructure.web.dto.DeleteObjectsRequest;
import com.miranda.luna.store.infrastructure.web.dto.GenerateUploadUrlRequest;
import com.miranda.luna.store.infrastructure.web.dto.ListObjectsResponse;
import com.miranda.luna.store.infrastructure.web.dto.PresignedUrlResponse;
import com.miranda.luna.store.infrastructure.web.dto.StoreObjectDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/store/buckets/{bucketName}/objects")
public class ObjectController {

    private final ObjectService objectService;

    public ObjectController(ObjectService objectService) {
        this.objectService = objectService;
    }

    @GetMapping
    public ResponseEntity<ListObjectsResponse> listObjects(
            @PathVariable("bucketName") String bucketName,
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "delimiter", required = false) String delimiter
    ) {
        return ResponseEntity.ok(objectService.listObjects(bucketName, prefix, delimiter));
    }

    @PostMapping("/folder")
    public ResponseEntity<StoreObjectDto> createFolder(
            @PathVariable("bucketName") String bucketName,
            @Valid @RequestBody CreateFolderRequest request
    ) {
        StoreObjectDto folder = objectService.createFolder(bucketName, request.folderPath());
        return ResponseEntity.status(HttpStatus.CREATED).body(folder);
    }

    @PostMapping("/upload-url")
    public ResponseEntity<PresignedUrlResponse> generateUploadPresignedUrl(
            @PathVariable("bucketName") String bucketName,
            @Valid @RequestBody GenerateUploadUrlRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String endpoint = resolvePublicEndpoint(request.publicEndpoint(), httpRequest);
        PresignedUrlResponse response = objectService.generateUploadPresignedUrl(
                bucketName,
                request.objectKey(),
                request.contentType(),
                request.durationMinutes(),
                endpoint
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download-url")
    public ResponseEntity<PresignedUrlResponse> generateDownloadPresignedUrl(
            @PathVariable("bucketName") String bucketName,
            @RequestParam("objectKey") String objectKey,
            @RequestParam(name = "durationMinutes", required = false) Long durationMinutes,
            @RequestParam(name = "publicEndpoint", required = false) String publicEndpoint,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String endpoint = resolvePublicEndpoint(publicEndpoint, httpRequest);
        PresignedUrlResponse response = objectService.generateDownloadPresignedUrl(
                bucketName,
                objectKey,
                durationMinutes,
                endpoint
        );
        return ResponseEntity.ok(response);
    }

    private String resolvePublicEndpoint(String explicitEndpoint, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (explicitEndpoint != null && !explicitEndpoint.isBlank()) {
            return explicitEndpoint.trim();
        }
        if (httpRequest == null) {
            return null;
        }
        String origin = httpRequest.getHeader("Origin");
        if (origin != null && origin.contains("console-lola.")) {
            return origin.replace("console-lola.", "s3-lola.");
        }
        String host = httpRequest.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = httpRequest.getHeader("Host");
        }
        if (host != null && host.startsWith("store-lola.")) {
            String proto = httpRequest.getHeader("X-Forwarded-Proto");
            if (proto == null || proto.isBlank()) {
                proto = httpRequest.isSecure() ? "https" : "http";
            }
            return proto + "://" + host.replace("store-lola.", "s3-lola.");
        }
        return null;
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteObject(
            @PathVariable("bucketName") String bucketName,
            @RequestParam("objectKey") String objectKey
    ) {
        objectService.deleteObject(bucketName, objectKey);
        return ResponseEntity.ok(Map.of("message", "Object deleted successfully"));
    }

    @PostMapping("/delete-batch")
    public ResponseEntity<Map<String, Object>> deleteObjectsBatch(
            @PathVariable("bucketName") String bucketName,
            @Valid @RequestBody DeleteObjectsRequest request
    ) {
        objectService.deleteObjects(bucketName, request.objectKeys());
        return ResponseEntity.ok(Map.of(
                "message", "Objects deleted successfully",
                "deletedCount", request.objectKeys().size()
        ));
    }
}
