package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.UserRole;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.frostnova.aigateway.chat.command.ChatCommand;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.chat.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
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

    @Test
    void returnsStreamingResponsesAsServerSentEvents() {
        LlmResponse chunk = new LlmResponse();
        chunk.setConversationId("conversation-test");
        chunk.setContent("hello");
        ChatService chatService = new ChatService(null, null, null, null) {
            @Override
            public Flux<LlmResponse> executeChatStream(ChatCommand command) {
                return Flux.just(chunk);
            }
        };
        ChatController controller = new ChatController(chatService);
        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserId(42L);
        principal.setRole(UserRole.USER);
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");

        List<ServerSentEvent<LlmResponse>> events = controller
                .streamCompletions(request, principal)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.event()).isEqualTo("message");
                    assertThat(event.data()).isSameAs(chunk);
                });
    }

    @Test
    void serializesStreamingResponsesWithMvcSseFormat() throws Exception {
        LlmResponse chunk = new LlmResponse();
        chunk.setConversationId("conversation-test");
        chunk.setContent("hello");
        ChatService chatService = new ChatService(null, null, null, null) {
            @Override
            public Flux<LlmResponse> executeChatStream(ChatCommand command) {
                return Flux.just(chunk);
            }
        };
        MockMvc mockMvc = standaloneSetup(new ChatController(chatService)).build();
        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserId(42L);
        principal.setRole(UserRole.USER);

        MvcResult pendingResult = mockMvc.perform(post("/api/v1/chat/completions/stream")
                        .requestAttr(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE, principal)
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("""
                                {
                                  "provider": "gemini",
                                  "model": "test-model",
                                  "userMessage": "hello"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pendingResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("\"conversationId\":\"conversation-test\"")))
                .andExpect(content().string(containsString("\"content\":\"hello\"")));
    }
}
