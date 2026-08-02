package org.frostnova.aigateway.service;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.frostnova.aigateway.usage.service.LlmRequestRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public LlmResponse executeChat(AppChatRequest request) {
        LlmProviderEnum provider = LlmProviderEnum.requireByCode(request.getProvider());
        String model = properties.requireSupportedModel(provider, request.getModel());
        LlmRequest llmRequest = toLlmRequest(request, model);

        String requestId = UUID.randomUUID().toString();
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startNanos = System.nanoTime();

        try {
            LlmResponse response = providerRegistry.getProvider(provider).chat(llmRequest);
            requestRecordService.recordSuccess(
                    requestId,
                    provider.getCode(),
                    model,
                    response,
                    elapsedMillis(startNanos),
                    requestedAt
            );
            return response;
        } catch (RuntimeException exception) {
            requestRecordService.recordFailure(
                    requestId,
                    provider.getCode(),
                    model,
                    exception,
                    elapsedMillis(startNanos),
                    requestedAt
            );
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

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
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
