package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.frostnova.aigateway.chat.command.ChatCommand;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.chat.ChatService;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/completions")
    public LlmResponse completions(
            @RequestBody AppChatRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            ChatCommand chatCommand = new ChatCommand(requestId, principal.getUserId(), request);
            return chatService.executeChat(chatCommand);
        }
    }

    @PostMapping(
            value = "/completions/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<LlmResponse>> streamCompletions(
            @RequestBody AppChatRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            ChatCommand chatCommand = new ChatCommand(requestId, principal.getUserId(), request);
            return chatService.executeChatStream(chatCommand)
                    .map(response -> ServerSentEvent.builder(response)
                            .event("message")
                            .build())
                    .onErrorResume(exception -> Flux.just(
                            ServerSentEvent.<LlmResponse>builder(streamError(exception))
                                    .event("error")
                                    .build()
                    ));
        }
    }

    private LlmResponse streamError(Throwable exception) {
        LlmResponse response = new LlmResponse();
        response.setContent(exception instanceof BaseException
                ? exception.getMessage()
                : "Chat request failed unexpectedly");
        return response;
    }
}
