package org.frostnova.aigateway.credential.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCredential {

    private Long id;
    private String appId;
    private String appName;
    private String apiKeyHash;
    private AppCredentialStatus status;
    private Integer rateLimitPerMinute;
    private Long dailyTokenQuota;
    private String allowedModels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
