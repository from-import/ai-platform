package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.service.ChatService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

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
            return chatService.executeChat(requestId, principal.getUserId(), request);
        }
    }
}
