package com.miranda.luna.store;

import com.jayway.jsonpath.JsonPath;
import com.miranda.luna.store.domain.model.BucketAccessPolicy;
import com.miranda.luna.store.domain.repository.StoreBucketRepository;
import com.miranda.luna.store.infrastructure.web.dto.CreateBucketRequest;
import com.miranda.luna.store.infrastructure.web.dto.CreateFolderRequest;
import com.miranda.luna.store.infrastructure.web.dto.DeleteObjectsRequest;
import com.miranda.luna.store.infrastructure.web.dto.GenerateUploadUrlRequest;
import com.miranda.luna.store.infrastructure.web.dto.UpdatePolicyRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class BucketAndObjectIntegrationTest {

    private static final String TEST_BUCKET = "lola-test-bucket";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreBucketRepository bucketRepository;

    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanupTestBucket();
    }

    @AfterEach
    void tearDown() {
        cleanupTestBucket();
    }

    private void cleanupTestBucket() {
        try {
            ListObjectsV2Response res = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(TEST_BUCKET).build());
            if (res.contents() != null && !res.contents().isEmpty()) {
                List<ObjectIdentifier> ids = res.contents().stream()
                        .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                        .toList();
                s3Client.deleteObjects(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.builder()
                        .bucket(TEST_BUCKET)
                        .delete(Delete.builder().objects(ids).build())
                        .build());
            }
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(TEST_BUCKET).build());
        } catch (Exception ignored) {
        }
        bucketRepository.findByName(TEST_BUCKET).ifPresent(bucketRepository::delete);
    }

    @Test
    void testSecurityUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/store/buckets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
    }

    @Test
    void testSecurityUnauthorizedWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/store/buckets")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
    }

    @Test
    void testSecurityForbiddenWithUnprivilegedToken() throws Exception {
        mockMvc.perform(get("/api/v1/store/buckets")
                        .header("Authorization", "Bearer valid-unauthorized-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
    }

    @Test
    void testBucketNameValidationFailure() throws Exception {
        // Uppercase not allowed by RFC 1123
        CreateBucketRequest req = new CreateBucketRequest(
                "InvalidNameBucket",
                "us-east-1",
                BucketAccessPolicy.PRIVATE,
                false
        );

        mockMvc.perform(post("/api/v1/store/buckets")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
    }

    @Test
    void testFullBucketAndObjectLifecycle() throws Exception {
        // 1. Create bucket
        CreateBucketRequest createRequest = new CreateBucketRequest(
                TEST_BUCKET,
                "us-east-1",
                BucketAccessPolicy.PRIVATE,
                false
        );

        mockMvc.perform(post("/api/v1/store/buckets")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.data.name").value(TEST_BUCKET))
                .andExpect(jsonPath("$.data.region").value("us-east-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessPolicy").value("PRIVATE"))
                .andExpect(jsonPath("$.data.versioningEnabled").value(false))
                .andExpect(jsonPath("$.data.lrn").value("lola:store:bucket:" + TEST_BUCKET));

        // 2. Get bucket by name
        mockMvc.perform(get("/api/v1/store/buckets/" + TEST_BUCKET)
                        .header("Authorization", "Bearer valid-store-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(TEST_BUCKET))
                .andExpect(jsonPath("$.data.accessPolicy").value("PRIVATE"));

        // 3. List buckets
        mockMvc.perform(get("/api/v1/store/buckets")
                        .header("Authorization", "Bearer valid-store-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 4. Create folder
        CreateFolderRequest folderReq = new CreateFolderRequest("photos/2026/");
        mockMvc.perform(post("/api/v1/store/buckets/" + TEST_BUCKET + "/objects/folder")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value("photos/2026/"))
                .andExpect(jsonPath("$.data.isFolder").value(true));

        // 5. Generate Presigned Upload URL
        GenerateUploadUrlRequest uploadUrlReq = new GenerateUploadUrlRequest(
                "photos/2026/sample.png",
                "image/png",
                30L
        );
        MvcResult uploadResult = mockMvc.perform(post("/api/v1/store/buckets/" + TEST_BUCKET + "/objects/upload-url")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadUrlReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.objectKey").value("photos/2026/sample.png"))
                .andExpect(jsonPath("$.data.bucketName").value(TEST_BUCKET))
                .andReturn();

        String uploadUrl = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.data.url");
        assertTrue(uploadUrl.contains(TEST_BUCKET));

        // 6. Upload a sample object directly via s3Client to simulate file uploaded
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(TEST_BUCKET)
                        .key("photos/2026/sample.png")
                        .contentType("image/png")
                        .build(),
                RequestBody.fromString("Sample image bytes placeholder")
        );

        // 7. Generate Presigned Download URL
        MvcResult downloadResult = mockMvc.perform(get("/api/v1/store/buckets/" + TEST_BUCKET + "/objects/download-url")
                        .header("Authorization", "Bearer valid-store-token")
                        .param("objectKey", "photos/2026/sample.png")
                        .param("durationMinutes", "45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andExpect(jsonPath("$.data.method").value("GET"))
                .andExpect(jsonPath("$.data.objectKey").value("photos/2026/sample.png"))
                .andReturn();

        String downloadUrl = JsonPath.read(downloadResult.getResponse().getContentAsString(), "$.data.url");
        assertTrue(downloadUrl.contains(TEST_BUCKET));

        // 8. List objects
        mockMvc.perform(get("/api/v1/store/buckets/" + TEST_BUCKET + "/objects")
                        .header("Authorization", "Bearer valid-store-token")
                        .param("prefix", "photos/")
                        .param("delimiter", "/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bucketName").value(TEST_BUCKET))
                .andExpect(jsonPath("$.data.prefix").value("photos/"));

        // 9. Update Access Policy to PUBLIC_READ
        UpdatePolicyRequest policyReq = new UpdatePolicyRequest(BucketAccessPolicy.PUBLIC_READ);
        mockMvc.perform(put("/api/v1/store/buckets/" + TEST_BUCKET + "/policy")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessPolicy").value("PUBLIC_READ"));

        // 10. Delete single object
        mockMvc.perform(delete("/api/v1/store/buckets/" + TEST_BUCKET + "/objects")
                        .header("Authorization", "Bearer valid-store-token")
                        .param("objectKey", "photos/2026/sample.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Object deleted successfully"));

        // 11. Put another file and test batch delete
        s3Client.putObject(
                PutObjectRequest.builder().bucket(TEST_BUCKET).key("batch1.txt").build(),
                RequestBody.fromString("1")
        );
        s3Client.putObject(
                PutObjectRequest.builder().bucket(TEST_BUCKET).key("batch2.txt").build(),
                RequestBody.fromString("2")
        );

        DeleteObjectsRequest batchReq = new DeleteObjectsRequest(List.of("batch1.txt", "batch2.txt"));
        mockMvc.perform(post("/api/v1/store/buckets/" + TEST_BUCKET + "/objects/delete-batch")
                        .header("Authorization", "Bearer valid-store-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));

        // 12. Delete bucket with force=true
        mockMvc.perform(delete("/api/v1/store/buckets/" + TEST_BUCKET)
                        .header("Authorization", "Bearer valid-store-token")
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Bucket deleted successfully"));

        // Verify bucket is removed from DB
        assertFalse(bucketRepository.findByName(TEST_BUCKET).isPresent());
    }

    @Test
    void testProtectedBackupBucketCannotBeDeleted() throws Exception {
        String backupBucketName = "test-system-backups";
        try {
            // 1. Create protected backup bucket
            CreateBucketRequest req = new CreateBucketRequest(
                    backupBucketName,
                    "lola-local",
                    BucketAccessPolicy.PRIVATE,
                    false,
                    true
            );

            mockMvc.perform(post("/api/v1/store/buckets")
                            .header("Authorization", "Bearer valid-store-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value(backupBucketName))
                    .andExpect(jsonPath("$.data.isProtected").value(true));

            // 2. Attempt deletion without force
            mockMvc.perform(delete("/api/v1/store/buckets/" + backupBucketName)
                            .header("Authorization", "Bearer valid-store-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("INVALID_OPERATION"))
                    .andExpect(jsonPath("$.data.message").value("Bucket '" + backupBucketName + "' is protected and cannot be deleted because it is reserved for system backups."));

            // 3. Attempt deletion with force=true
            mockMvc.perform(delete("/api/v1/store/buckets/" + backupBucketName)
                            .header("Authorization", "Bearer valid-store-token")
                            .param("force", "true"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("INVALID_OPERATION"));

            // 4. Verify bucket still exists in repository
            assertTrue(bucketRepository.findByName(backupBucketName).isPresent());
        } finally {
            bucketRepository.findByName(backupBucketName).ifPresent(bucketRepository::delete);
            try {
                s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(backupBucketName).build());
            } catch (Exception ignored) {
            }
        }
    }
}
