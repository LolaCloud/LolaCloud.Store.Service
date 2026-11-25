package com.miranda.luna.store.infrastructure.security;

import com.miranda.luna.store.domain.model.AuthenticatedOperator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Primary
public class TestLciAuthClient implements LciAuthClientPort {

    private final LciAuthClient realClient;

    public TestLciAuthClient(LciAuthClient realClient) {
        this.realClient = realClient;
    }

    @Override
    public Optional<AuthenticatedOperator> introspect(String token) {
        if ("valid-store-token".equals(token) || "test-admin-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.fromString("90f815ae-d8fb-481d-ac71-cc764b23abcd"),
                    "admin",
                    List.of("store::*")
            ));
        }

        if ("valid-read-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "store-reader",
                    List.of("store::bucket::read", "store::object::read")
            ));
        }

        if ("valid-root-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "root",
                    List.of("*::*::*")
            ));
        }

        if ("valid-unauthorized-token".equals(token)) {
            return Optional.of(new AuthenticatedOperator(
                    UUID.randomUUID(),
                    "unprivileged",
                    List.of("other::service")
            ));
        }

        if ("invalid-token".equals(token) || "expired-token".equals(token)) {
            return Optional.empty();
        }

        return realClient.introspect(token);
    }
}
