package com.miranda.luna.store.domain.model;

import java.util.List;
import java.util.UUID;

public record AuthenticatedOperator(
        UUID id,
        String username,
        List<String> permissions
) {
    public boolean can(String permission) {
        if (permission == null || permissions == null) {
            return false;
        }
        if (permissions.contains("*") || permissions.contains("*::*::*")) {
            return true;
        }
        if (permissions.contains(permission)) {
            return true;
        }
        String[] requestedParts = permission.split("::");
        for (String perm : permissions) {
            String[] permParts = perm.split("::");
            if (matchesParts(permParts, requestedParts)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesParts(String[] permParts, String[] reqParts) {
        if (permParts.length == 1 && "*".equals(permParts[0])) {
            return true;
        }
        if (permParts.length == 2 && "*".equals(permParts[1]) && reqParts.length >= 2) {
            return permParts[0].equalsIgnoreCase(reqParts[0]);
        }
        if (permParts.length != reqParts.length) {
            return false;
        }
        for (int i = 0; i < permParts.length; i++) {
            if ("*".equals(permParts[i])) {
                continue;
            }
            if (!permParts[i].equalsIgnoreCase(reqParts[i])) {
                return false;
            }
        }
        return true;
    }
}
