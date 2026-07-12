package org.frostnova.aigateway.service;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.prompt.PromptTemplateManager;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.ProviderRouter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// core/ChatService.java
@Service
public class ChatService {

    private final PromptTemplateManager promptManager;
    private final ProviderRouter providerRouter;
    private final AiGatewayProperties properties;

    public ChatService(PromptTemplateManager promptManager, ProviderRouter providerRouter, AiGatewayProperties properties) {
        this.promptManager = promptManager;
        this.providerRouter = providerRouter;
        this.properties = properties;
    }

    public LlmResponse executeChat(AppChatRequest request) {
        AiGatewayProperties.ModelEndpoint endpoint = resolveEndpoint(request.getModelAlias());

        // 1. 获取并组装 System Prompt
        String systemPrompt = promptManager.renderPrompt(request.getPromptId(), request);

        // 2. 组装标准 LlmRequest
        LlmRequest llmRequest = new LlmRequest();
        llmRequest.setModel(endpoint.getModel());
        llmRequest.addMessage("system", systemPrompt);
        if (request.getUserMessage() != null) {
            llmRequest.addMessage("user", request.getUserMessage());
        }

        // 3. 路由到具体的模型厂商
        LlmProvider provider = providerRouter.getProvider(endpoint.getProviderCode().getCode());

        // 4. 调用大模型
        LlmResponse response = provider.chat(llmRequest);

        // 5. 返回结果
        return response;
    }

    private AiGatewayProperties.ModelEndpoint resolveEndpoint(String requestedModelAlias) {
        String modelAlias = requestedModelAlias == null || requestedModelAlias.isBlank()
                ? properties.getDefaultModelAlias()
                : requestedModelAlias;
        AiGatewayProperties.ModelAlias alias = properties.getModelAliases().get(modelAlias);
        if (alias == null) {
            throw new IllegalArgumentException("Unknown model alias: " + modelAlias);
        }

        List<String> endpointIds = new ArrayList<>();
        endpointIds.add(alias.getPrimary());
        endpointIds.addAll(alias.getFallback());

        for (String endpointId : endpointIds) {
            AiGatewayProperties.ModelEndpoint endpoint = findEnabledEndpoint(endpointId);
            if (endpoint != null && providerRouter.hasProvider(endpoint.getProviderCode().getCode())) {
                return endpoint;
            }
        }

        throw new IllegalStateException("No available endpoint for model alias: " + modelAlias);
    }

    private AiGatewayProperties.ModelEndpoint findEnabledEndpoint(String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return null;
        }
        return properties.getEndpoints().stream()
                .filter(item -> endpointId.equals(item.getEndpointId()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .findFirst()
                .orElse(null);
    }
}
