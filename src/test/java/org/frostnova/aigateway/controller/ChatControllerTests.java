package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTests {

    @AfterEach
    void clearMdc() {
        MDC.remove("requestId");
    }

    @Test
    void generatesAndPropagatesRequestIdWithinMdcScope() {
        ChatService chatService = mock(ChatService.class);
        ChatController controller = new ChatController(chatService);
        AppChatRequest request = new AppChatRequest();
        LlmResponse expectedResponse = new LlmResponse();
        AtomicReference<String> capturedRequestId = new AtomicReference<>();

        when(chatService.executeChat(anyString(), same(request))).thenAnswer(invocation -> {
            String requestId = invocation.getArgument(0);
            capturedRequestId.set(requestId);
            assertThat(MDC.get("requestId")).isEqualTo(requestId);
            return expectedResponse;
        });

        LlmResponse response = controller.completions(request);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(capturedRequestId.get()).isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }
}
