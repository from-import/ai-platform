package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final AiGatewayProperties properties;

    public ModelController(AiGatewayProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public List<ModelInfo> listModels() {
        List<ModelInfo> models = new ArrayList<>();
        for (LlmProviderEnum provider : LlmProviderEnum.values()) {
            AiGatewayProperties.ProviderConfig config = properties.getProviders().get(provider);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                continue;
            }
            for (String model : config.getSupportedModels()) {
                models.add(new ModelInfo(provider.getCode(), model));
            }
        }
        return List.copyOf(models);
    }

    public record ModelInfo(String provider, String model) {
    }
}
