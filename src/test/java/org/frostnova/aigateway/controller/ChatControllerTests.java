package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.UserRole;
import org.frostnova.aigateway.chat.command.ChatCommand;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.chat.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
class ChatControllerTests {

    @AfterEach
    void clearMdc() {
        MDC.remove("requestId");
    }

    @Test
    void generatesAndPropagatesRequestIdWithinMdcScope() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        LlmResponse expectedResponse = new LlmResponse();
        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        ChatService chatService = new ChatService(null, null, null, null) {
            @Override
            public LlmResponse executeChat(ChatCommand command) {
                assertThat(command.getRequest()).isSameAs(request);
                assertThat(command.getUserId()).isEqualTo(42L);
                capturedRequestId.set(command.getRequestId());
                assertThat(MDC.get("requestId")).isEqualTo(command.getRequestId());
                return expectedResponse;
            }
        };
        ChatController controller = new ChatController(chatService);
        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserId(42L);
        principal.setRole(UserRole.USER);

        LlmResponse response = controller.completions(request, principal);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(capturedRequestId.get()).isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }
}
