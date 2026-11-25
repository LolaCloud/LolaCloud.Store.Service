package com.miranda.luna.store.infrastructure.security;

import com.miranda.luna.store.domain.model.AuthenticatedOperator;

import java.util.Optional;

public interface LciAuthClientPort {
    Optional<AuthenticatedOperator> introspect(String token);
}
