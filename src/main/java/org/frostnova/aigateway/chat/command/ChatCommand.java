package org.frostnova.aigateway.chat.command;

import lombok.Getter;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.usage.model.LlmRequestRecordContext;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
public class ChatCommand {

    private final String requestId;
    private final Long userId;
    private final AppChatRequest request;
    private final LlmProviderEnum providerEnum;

    public ChatCommand(String requestId, Long userId, AppChatRequest request) {
        this.requestId = requestId;
        this.userId = userId;
        this.request = request;
        this.providerEnum = LlmProviderEnum.requireByCode(request.getProvider());
    }

    public LlmRequestRecordContext generateRecordContext(String model) {
        return LlmRequestRecordContext.builder()
                .userId(userId)
                .requestId(requestId)
                .provider(providerEnum.getCode())
                .model(model)
                .requestedAt(LocalDateTime.now(ZoneOffset.UTC))
                .startedAtNanos(System.nanoTime())
                .build();
    }
}
