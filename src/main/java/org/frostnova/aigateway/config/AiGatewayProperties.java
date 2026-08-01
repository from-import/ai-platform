package org.frostnova.aigateway.config;

import lombok.Data;
import org.frostnova.aigateway.provider.LlmProviderEnum;
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
    private Set<String> supportedModels = new LinkedHashSet<>();

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKeyEnv;
        private Boolean enabled = true;
    }

    public ProviderConfig requireProvider(LlmProviderEnum providerCode) {
        ProviderConfig provider = providers.get(providerCode);
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            throw new IllegalStateException("Provider is not configured or enabled: " + providerCode.getCode());
        }
        return provider;
    }
}
