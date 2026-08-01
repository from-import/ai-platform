package org.frostnova.aigateway.service;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ProviderRegistry providerRegistry;
    private final AiGatewayProperties properties;

    public ChatService(ProviderRegistry providerRegistry, AiGatewayProperties properties) {
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    public LlmResponse executeChat(AppChatRequest request) {
        String requestedModel = requireModel(request.getModel());
        int separatorIndex = requestedModel.indexOf('/');
        if (separatorIndex <= 0 || separatorIndex == requestedModel.length() - 1) {
            throw new IllegalArgumentException("Model must use provider/model format");
        }

        String providerPrefix = requestedModel.substring(0, separatorIndex);
        String upstreamModel = requestedModel.substring(separatorIndex + 1);
        LlmProviderEnum providerCode = LlmProviderEnum.getProviderByCode(providerPrefix);
        if (providerCode == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerPrefix);
        }
        if (!properties.getSupportedModels().contains(requestedModel)) {
            throw new IllegalArgumentException("Unsupported model: " + requestedModel);
        }

        LlmRequest llmRequest = new LlmRequest();
        llmRequest.setModel(upstreamModel);
        if (request.getUserMessage() != null) {
            llmRequest.addMessage("user", request.getUserMessage());
        }

        LlmProvider provider = providerRegistry.getProvider(providerCode);
        return provider.chat(llmRequest);
    }

    private String requireModel(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            throw new IllegalArgumentException("Model must not be blank");
        }
        return requestedModel;
    }
}
