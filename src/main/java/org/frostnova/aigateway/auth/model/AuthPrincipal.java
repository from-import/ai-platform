package org.frostnova.aigateway.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthPrincipal {
    private Long sessionId;
    private Long userId;
    private String username;
    private String displayName;
    private UserRole role;
    private LocalDateTime expiresAt;
}
