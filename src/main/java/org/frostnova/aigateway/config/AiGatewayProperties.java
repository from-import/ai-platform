package org.frostnova.aigateway.config;

import lombok.Data;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.http.HttpStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "ai.gateway")
public class AiGatewayProperties {

    private Map<LlmProviderEnum, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKeyEnv;
        private Boolean enabled = true;
        private Set<String> supportedModels = new LinkedHashSet<>();
    }

    public ProviderConfig requireProvider(LlmProviderEnum providerCode) {
        ProviderConfig provider = providers.get(providerCode);
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BaseException(
                    ErrorCodes.PROVIDER_UNAVAILABLE,
                    "Provider is not configured or enabled: " + providerCode.getCode()
            );
        }
        return provider;
    }

    public String requireSupportedModel(LlmProviderEnum providerCode, String model) {
        if (model == null || model.isBlank()) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "Model must not be blank");
        }

        ProviderConfig provider = requireProvider(providerCode);
        if (!provider.getSupportedModels().contains(model)) {
            throw new BaseException(
                    ErrorCodes.UNSUPPORTED_MODEL,
                    "Unsupported model for provider " + providerCode.getCode() + ": " + model,
                    HttpStatus.BAD_REQUEST
            );
        }
        return model;
    }
}
