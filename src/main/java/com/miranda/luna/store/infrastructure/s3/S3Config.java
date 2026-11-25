package com.miranda.luna.store.infrastructure.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${lola.store.s3.endpoint:http://127.0.0.1:9000}")
    private String endpoint;

    @Value("${lola.store.s3.region:us-east-1}")
    private String region;

    @Value("${lola.store.s3.access-key:lola_admin}")
    private String accessKey;

    @Value("${lola.store.s3.secret-key:lola_admin_secret}")
    private String secretKey;

    @Value("${lola.store.s3.path-style-access-enabled:true}")
    private boolean pathStyleAccessEnabled;

    @Bean
    public S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccessEnabled)
                .build();
    }

    @Bean
    public S3Client s3Client(S3Configuration s3Configuration) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Configuration s3Configuration) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(s3Configuration)
                .build();
    }
}
