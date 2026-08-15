package org.frostnova.aigateway.auth.model;

public enum UserRole {
    USER,
    ADMIN;

    public boolean canViewAllUsage() {
        return this == ADMIN;
    }
}
