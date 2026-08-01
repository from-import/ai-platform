package org.frostnova.aigateway.credential.service;

import org.frostnova.aigateway.credential.mapper.AppCredentialMapper;
import org.frostnova.aigateway.credential.model.AppCredential;
import org.frostnova.aigateway.credential.model.AppCredentialStatus;
import org.frostnova.aigateway.credential.model.IssuedAppCredential;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppCredentialService {

    private static final int API_KEY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppCredentialMapper credentialMapper;

    public AppCredentialService(AppCredentialMapper credentialMapper) {
        this.credentialMapper = credentialMapper;
    }

    @Transactional
    public IssuedAppCredential issueCredential(
            String appName,
            int rateLimitPerMinute,
            long dailyTokenQuota,
            String allowedModels
    ) {
        validateIssueRequest(appName, rateLimitPerMinute, dailyTokenQuota);

        String appId = "app_" + UUID.randomUUID().toString().replace("-", "");
        String apiKey = generateApiKey();
        LocalDateTime now = LocalDateTime.now();

        AppCredential credential = AppCredential.builder()
                .appId(appId)
                .appName(appName.trim())
                .apiKeyHash(hashApiKey(apiKey))
                .status(AppCredentialStatus.ACTIVE)
                .rateLimitPerMinute(rateLimitPerMinute)
                .dailyTokenQuota(dailyTokenQuota)
                .allowedModels(normalizeAllowedModels(allowedModels))
                .createdAt(now)
                .updatedAt(now)
                .build();

        credentialMapper.insert(credential);
        return new IssuedAppCredential(credential.getId(), appId, apiKey);
    }

    @Transactional(readOnly = true)
    public Optional<AppCredential> findActiveByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(credentialMapper.findActiveByApiKeyHash(hashApiKey(apiKey)));
    }

    @Transactional
    public boolean changeStatus(String appId, AppCredentialStatus status) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return credentialMapper.updateStatus(appId, status) == 1;
    }

    private void validateIssueRequest(String appName, int rateLimitPerMinute, long dailyTokenQuota) {
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("appName must not be blank");
        }
        if (rateLimitPerMinute <= 0) {
            throw new IllegalArgumentException("rateLimitPerMinute must be positive");
        }
        if (dailyTokenQuota <= 0) {
            throw new IllegalArgumentException("dailyTokenQuota must be positive");
        }
    }

    private String normalizeAllowedModels(String allowedModels) {
        return allowedModels == null || allowedModels.isBlank()
                ? "[]"
                : allowedModels.trim();
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "aip_sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
