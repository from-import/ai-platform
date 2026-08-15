package org.frostnova.aigateway.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccount {
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private UserStatus status;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
