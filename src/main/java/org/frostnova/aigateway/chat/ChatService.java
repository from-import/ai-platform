package org.frostnova.aigateway.chat;

import lombok.extern.slf4j.Slf4j;
import org.frostnova.aigateway.chat.command.ChatCommand;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.conversation.manager.ConversationManager;
import org.frostnova.aigateway.conversation.model.ChatConversation;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.domain.model.Message;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.frostnova.aigateway.usage.model.LlmRequestRecordContext;
import org.frostnova.aigateway.usage.service.LlmRequestRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ChatService {

    private final ProviderRegistry providerRegistry;
    private final AiGatewayProperties properties;
    private final LlmRequestRecordService requestRecordService;
    private final ConversationManager conversationManager;

    public ChatService(
            ProviderRegistry providerRegistry,
            AiGatewayProperties properties,
            LlmRequestRecordService requestRecordService,
            ConversationManager conversationManager
    ) {
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.requestRecordService = requestRecordService;
        this.conversationManager = conversationManager;
    }

    public LlmResponse executeChat(ChatCommand chatCommand) {

        String requestId = chatCommand.getRequestId();
        AppChatRequest request = chatCommand.getRequest();
        LlmProviderEnum providerEnum = chatCommand.getProviderEnum();
        String model = properties.requireSupportedModel(providerEnum, request.getModel());
        String userMessage = requireUserMessage(request);

        ChatConversation conversation = conversationManager.resolveConversation(
                chatCommand.getUserId(),
                request
        );
        conversationManager.appendMessage(conversation, ConversationRole.USER, userMessage);
        List<Message> history = conversationManager.loadMessageHistory(conversation);
        LlmRequest llmRequest = toLlmRequest(history, model);

        LlmRequestRecordContext recordContext = chatCommand.generateRecordContext(model);

        LlmResponse response;
        try {
            LlmProvider provider = providerRegistry.getProvider(providerEnum);
            response = provider.chat(requestId, llmRequest);
        } catch (RuntimeException exception) {
            recordContext.recordEndTime();
            requestRecordService.recordFailure(recordContext, exception);
            logFailure(recordContext, exception);
            throw toChatException(exception);
        }

        response.setConversationId(conversation.getId());
        conversationManager.appendMessage(
                conversation,
                ConversationRole.ASSISTANT,
                response.getContent() == null ? "" : response.getContent()
        );
        recordContext.recordEndTime();
        requestRecordService.recordSuccess(recordContext, response);
        logSuccess(recordContext);
        return response;
    }

    private void logSuccess(LlmRequestRecordContext context) {
        log.atInfo()
                .addKeyValue("event.action", "llm.chat")
                .addKeyValue("event.outcome", "success")
                .addKeyValue("event.duration", context.getDurationNanos())
                .addKeyValue("provider", context.getProvider())
                .addKeyValue("model", context.getModel())
                .log("LLM request completed");
    }

    private void logFailure(
            LlmRequestRecordContext context,
            RuntimeException exception
    ) {
        log.atError()
                .addKeyValue("event.action", "llm.chat")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.duration", context.getDurationNanos())
                .addKeyValue("error.code", errorCode(exception))
                .addKeyValue("provider", context.getProvider())
                .addKeyValue("model", context.getModel())
                .setCause(exception)
                .log("LLM request failed");
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BaseException baseException) {
            return baseException.getCode();
        }
        return ErrorCodes.INTERNAL_SERVER_ERROR;
    }

    private RuntimeException toChatException(RuntimeException exception) {
        if (exception instanceof BaseException) {
            return exception;
        }
        return new BaseException(
                ErrorCodes.INTERNAL_SERVER_ERROR,
                "Chat request failed unexpectedly",
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception
        );
    }

    private String requireUserMessage(AppChatRequest request) {
        if (request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "User message must not be blank"
            );
        }
        return request.getUserMessage();
    }

    private LlmRequest toLlmRequest(List<Message> history, String model) {
        LlmRequest llmRequest = new LlmRequest();
        llmRequest.setModel(model);
        llmRequest.setMessages(history);
        return llmRequest;
    }
}
