package com.miranda.luna.store.infrastructure.web.controller;

import com.miranda.luna.store.application.service.BucketService;
import com.miranda.luna.store.infrastructure.web.dto.BucketResponse;
import com.miranda.luna.store.infrastructure.web.dto.CreateBucketRequest;
import com.miranda.luna.store.infrastructure.web.dto.UpdatePolicyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/store/buckets")
public class BucketController {

    private final BucketService bucketService;

    public BucketController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @GetMapping
    public ResponseEntity<List<BucketResponse>> listBuckets() {
        return ResponseEntity.ok(bucketService.listBuckets());
    }

    @GetMapping("/{name}")
    public ResponseEntity<BucketResponse> getBucket(@PathVariable("name") String name) {
        return ResponseEntity.ok(bucketService.getBucket(name));
    }

    @PostMapping
    public ResponseEntity<BucketResponse> createBucket(@Valid @RequestBody CreateBucketRequest request) {
        BucketResponse response = bucketService.createBucket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> deleteBucket(
            @PathVariable("name") String name,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        bucketService.deleteBucket(name, force);
        return ResponseEntity.ok(Map.of("message", "Bucket deleted successfully"));
    }

    @PutMapping("/{name}/policy")
    public ResponseEntity<BucketResponse> updateAccessPolicy(
            @PathVariable("name") String name,
            @Valid @RequestBody UpdatePolicyRequest request
    ) {
        return ResponseEntity.ok(bucketService.updateAccessPolicy(name, request.accessPolicy()));
    }
}
