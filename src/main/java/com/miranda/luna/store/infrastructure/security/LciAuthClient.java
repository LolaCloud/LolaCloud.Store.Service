package com.miranda.luna.store.infrastructure.security;

import com.miranda.luna.store.domain.model.AuthenticatedOperator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LciAuthClient implements LciAuthClientPort {

    private static final Logger logger = LoggerFactory.getLogger(LciAuthClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String introspectUrl;
    private final SecretKey signingKey;

    public LciAuthClient(
            ObjectMapper objectMapper,
            @Value("${lola.lci.auth.introspect-url:http://localhost:8080/internal/v1/auth/introspect}") String introspectUrl,
            @Value("${lola.security.jwt.secret:lola-cloud-platform-secret-encryption-key-for-jwt-signing-256-bits-minimum}") String secret
    ) {
        this.objectMapper = objectMapper;
        this.introspectUrl = introspectUrl;
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public Optional<AuthenticatedOperator> introspect(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        // 1. First attempt direct JWT verification if valid signed token
        Optional<AuthenticatedOperator> jwtResult = tryVerifyJwt(token);
        if (jwtResult.isPresent()) {
            return jwtResult;
        }

        // 2. Fall back to remote LCI introspection endpoint
        return tryRemoteIntrospect(token);
    }

    @SuppressWarnings("unchecked")
    private Optional<AuthenticatedOperator> tryVerifyJwt(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }

            String idStr = claims.get("operatorId", String.class);
            if (idStr == null) {
                idStr = claims.get("id", String.class);
            }
            UUID operatorId = idStr != null ? UUID.fromString(idStr) : UUID.randomUUID();

            List<String> permissions = new ArrayList<>();
            Object permsObj = claims.get("permissions");
            if (permsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        permissions.add(item.toString());
                    }
                }
            }

            return Optional.of(new AuthenticatedOperator(operatorId, username, permissions));
        } catch (Exception e) {
            logger.debug("Token is not a locally signed JWT or signature mismatch: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AuthenticatedOperator> tryRemoteIntrospect(String token) {
        try {
            String requestPayload = objectMapper.writeValueAsString(Map.of("token", token));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(introspectUrl))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("LCI introspect returned non-2xx status: {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataNode = root.has("data") ? root.get("data") : root;

            boolean valid = dataNode.path("valid").asBoolean(false);
            if (!valid) {
                String error = dataNode.path("error").asText("Unknown error");
                logger.debug("LCI token introspection declared token invalid: {}", error);
                return Optional.empty();
            }

            JsonNode operatorNode = dataNode.get("operator");
            if (operatorNode == null || operatorNode.isNull()) {
                return Optional.empty();
            }

            String idStr = operatorNode.path("id").asText(null);
            UUID id = idStr != null ? UUID.fromString(idStr) : UUID.randomUUID();
            String username = operatorNode.path("username").asText();

            List<String> permissions = new ArrayList<>();
            JsonNode permsNode = operatorNode.get("permissions");
            if (permsNode != null && permsNode.isArray()) {
                for (JsonNode p : permsNode) {
                    permissions.add(p.asText());
                }
            }

            return Optional.of(new AuthenticatedOperator(id, username, permissions));
        } catch (Exception e) {
            logger.debug("Failed communicating with LCI token introspection endpoint: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
