package org.frostnova.aigateway.config;

import lombok.Data;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "ai.gateway")
public class AiGatewayProperties {

    private String defaultModelAlias;
    private List<ModelEndpoint> endpoints = new ArrayList<>();
    private Map<String, ModelAlias> modelAliases = new HashMap<>();

    @Data
    public static class ModelEndpoint {
        private String endpointId;
        private LlmProviderEnum providerCode;
        private DeploymentType deploymentType;
        private String model;
        private String baseUrl;
        private String apiKeyEnv;
        private Integer timeoutMs = 30_000;
        private Integer maxRetries = 0;
        private Integer priority = 100;
        private Boolean enabled = true;
    }

    @Data
    public static class ModelAlias {
        private String primary;
        private List<String> fallback = new ArrayList<>();
    }
}
