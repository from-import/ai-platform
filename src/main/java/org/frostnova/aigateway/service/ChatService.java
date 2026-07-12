package org.frostnova.aigateway.service;

import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.prompt.PromptTemplateManager;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRouter;
import org.springframework.stereotype.Service;

// core/ChatService.java
@Service
public class ChatService {

    private final PromptTemplateManager promptManager;
    private final ProviderRouter providerRouter;

    public ChatService(PromptTemplateManager promptManager, ProviderRouter providerRouter) {
        this.promptManager = promptManager;
        this.providerRouter = providerRouter;
    }

    public LlmResponse executeChat(AppChatRequest request) {
        // 1. 获取并组装 System Prompt
        String systemPrompt = promptManager.renderPrompt(request.getPromptId(), request);

        // 2. 组装标准 LlmRequest
        LlmRequest llmRequest = new LlmRequest();
        llmRequest.addMessage("system", systemPrompt);
        if (request.getUserMessage() != null) {
            llmRequest.addMessage("user", request.getUserMessage());
        }

        // 3. 路由到具体的模型厂商
        LlmProvider provider = providerRouter.getProvider(LlmProviderEnum.GEMINI.getCode());

        // 4. 调用大模型
        LlmResponse response = provider.chat(llmRequest);

        // 5. 返回结果
        return response;
    }
}
