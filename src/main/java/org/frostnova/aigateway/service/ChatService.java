package org.frostnova.aigateway.service;

import lombok.extern.slf4j.Slf4j;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.frostnova.aigateway.usage.service.LlmRequestRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatService {

    private final ProviderRegistry providerRegistry;
    private final AiGatewayProperties properties;
    private final LlmRequestRecordService requestRecordService;

    public ChatService(
            ProviderRegistry providerRegistry,
            AiGatewayProperties properties,
            LlmRequestRecordService requestRecordService
    ) {
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.requestRecordService = requestRecordService;
    }

    public LlmResponse executeChat(String requestId, Long userId, AppChatRequest request) {
        LlmProviderEnum providerEnum = LlmProviderEnum.requireByCode(request.getProvider());
        String model = properties.requireSupportedModel(providerEnum, request.getModel());
        LlmRequest llmRequest = toLlmRequest(request, model);

        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startNanos = System.nanoTime();

        try {
            LlmProvider provider = providerRegistry.getProvider(providerEnum);
            LlmResponse response = provider.chat(requestId, llmRequest);
            long durationNanos = elapsedNanos(startNanos);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            requestRecordService.recordSuccess(
                    userId,
                    requestId,
                    providerEnum.getCode(),
                    model,
                    response,
                    latencyMs,
                    requestedAt
            );
            log.atInfo()
                    .addKeyValue("event.action", "llm.chat")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("event.duration", durationNanos)
                    .addKeyValue("provider", providerEnum.getCode())
                    .addKeyValue("model", model)
                    .log("LLM request completed");
            return response;
        } catch (RuntimeException exception) {
            long durationNanos = elapsedNanos(startNanos);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            requestRecordService.recordFailure(
                    userId,
                    requestId,
                    providerEnum.getCode(),
                    model,
                    exception,
                    latencyMs,
                    requestedAt
            );
            log.atError()
                    .addKeyValue("event.action", "llm.chat")
                    .addKeyValue("event.outcome", "failure")
                    .addKeyValue("event.duration", durationNanos)
                    .addKeyValue("error.code", errorCode(exception))
                    .addKeyValue("provider", providerEnum.getCode())
                    .addKeyValue("model", model)
                    .setCause(exception)
                    .log("LLM request failed");
            if (exception instanceof BaseException baseException) {
                throw baseException;
            }
            throw new BaseException(
                    ErrorCodes.INTERNAL_SERVER_ERROR,
                    "Chat request failed unexpectedly",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private long elapsedNanos(long startNanos) {
        return System.nanoTime() - startNanos;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BaseException baseException) {
            return baseException.getCode();
        }
        return ErrorCodes.INTERNAL_SERVER_ERROR;
    }

    private LlmRequest toLlmRequest(AppChatRequest request, String model) {
        LlmRequest llmRequest = new LlmRequest();
        llmRequest.setModel(model);
        if (request.getUserMessage() != null) {
            llmRequest.addMessage("user", request.getUserMessage());
        }
        return llmRequest;
    }
}
