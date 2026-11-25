package com.miranda.luna.store.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedOperatorTest {

    @Test
    void testWildcardPermissions() {
        AuthenticatedOperator root = new AuthenticatedOperator(UUID.randomUUID(), "root", List.of("*"));
        assertTrue(root.can("store::bucket::create"));
        assertTrue(root.can("store::object::delete"));

        AuthenticatedOperator allWildcards = new AuthenticatedOperator(UUID.randomUUID(), "admin", List.of("*::*::*"));
        assertTrue(allWildcards.can("store::bucket::read"));
        assertTrue(allWildcards.can("relate::instance::manage"));
    }

    @Test
    void testStoreWildcard() {
        AuthenticatedOperator storeAdmin = new AuthenticatedOperator(UUID.randomUUID(), "storeadmin", List.of("store::*"));
        assertTrue(storeAdmin.can("store::bucket::create"));
        assertTrue(storeAdmin.can("store::object::read"));
        assertFalse(storeAdmin.can("runway::domain::create"));
    }

    @Test
    void testStoreBucketWildcard() {
        AuthenticatedOperator bucketAdmin = new AuthenticatedOperator(UUID.randomUUID(), "bucketadmin", List.of("store::bucket::*"));
        assertTrue(bucketAdmin.can("store::bucket::create"));
        assertTrue(bucketAdmin.can("store::bucket::delete"));
        assertFalse(bucketAdmin.can("store::object::create"));
    }

    @Test
    void testExactPermission() {
        AuthenticatedOperator reader = new AuthenticatedOperator(UUID.randomUUID(), "reader", List.of("store::bucket::read"));
        assertTrue(reader.can("store::bucket::read"));
        assertFalse(reader.can("store::bucket::create"));
        assertFalse(reader.can("store::object::read"));
    }
}
