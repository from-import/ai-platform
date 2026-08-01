package org.frostnova.aigateway.service;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTests {

    private AiGatewayProperties properties;
    private CapturingProvider geminiProvider;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        properties = new AiGatewayProperties();
        properties.setSupportedModels(Set.of("gemini/test-model"));
        geminiProvider = new CapturingProvider(LlmProviderEnum.GEMINI);
        chatService = new ChatService(
                new ProviderRegistry(List.of(geminiProvider)),
                properties
        );
    }

    @Test
    void routesNamespacedModelToProviderAndPassesUpstreamModel() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("gemini/test-model");
        request.setUserMessage("hello");

        LlmResponse response = chatService.executeChat(request);

        assertThat(response.getContent()).isEqualTo("ok");
        assertThat(geminiProvider.lastRequest.getModel()).isEqualTo("test-model");
        assertThat(geminiProvider.lastRequest.getMessages())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getRole()).isEqualTo("user");
                    assertThat(message.getContent()).isEqualTo("hello");
                });
    }

    @Test
    void rejectsModelThatIsNotInAllowlist() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("gemini/unknown-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported model: gemini/unknown-model");
    }

    @Test
    void rejectsModelWithoutProviderPrefix() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("test-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Model must use provider/model format");
    }

    @Test
    void rejectsUnknownProviderBeforeCheckingModelAllowlist() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("unknown/test-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported provider: unknown");
    }

    @Test
    void rejectsDuplicateProviderRegistration() {
        LlmProvider duplicate = new CapturingProvider(LlmProviderEnum.GEMINI);

        assertThatThrownBy(() -> new ProviderRegistry(List.of(geminiProvider, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate provider registered: gemini");
    }

    private static final class CapturingProvider implements LlmProvider {

        private final LlmProviderEnum providerCode;
        private LlmRequest lastRequest;

        private CapturingProvider(LlmProviderEnum providerCode) {
            this.providerCode = providerCode;
        }

        @Override
        public LlmProviderEnum getProviderCode() {
            return providerCode;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            lastRequest = request;
            LlmResponse response = new LlmResponse();
            response.setContent("ok");
            response.setProviderName(providerCode.getCode());
            return response;
        }
    }
}
