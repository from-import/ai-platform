package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroqProviderTests {

    @Test
    void mapsContentDeltaAndUsageFromStreamChunk() {
        GroqProvider provider = new GroqProvider(properties());

        LlmResponse response = provider.toStreamResponse("""
                {
                  "choices": [{"delta": {"content": "hello"}}],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 8,
                    "total_tokens": 20
                  }
                }
                """);

        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getProviderName()).isEqualTo("groq");
        assertThat(response.getPromptTokens()).isEqualTo(12);
        assertThat(response.getCompletionTokens()).isEqualTo(8);
        assertThat(response.getTotalTokens()).isEqualTo(20);
    }

    @Test
    void mapsUsageOnlyFinalStreamChunk() {
        GroqProvider provider = new GroqProvider(properties());

        LlmResponse response = provider.toStreamResponse("""
                {
                  "choices": [],
                  "usage": {
                    "prompt_tokens": 3,
                    "completion_tokens": 2,
                    "total_tokens": 5
                  }
                }
                """);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalTokens()).isEqualTo(5);
    }

    private AiGatewayProperties properties() {
        AiGatewayProperties.ProviderConfig config = new AiGatewayProperties.ProviderConfig();
        config.setBaseUrl("http://localhost");
        config.setApiKeyEnv("TEST_GROQ_API_KEY");
        config.setEnabled(true);

        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setProviders(Map.of(LlmProviderEnum.GROQ, config));
        return properties;
    }
}
