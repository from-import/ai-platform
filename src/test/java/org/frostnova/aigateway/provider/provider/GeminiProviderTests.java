package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiProviderTests {

    @Test
    void mapsContentAndTokenUsageFromGeminiResponse() throws Exception {
        GeminiProvider provider = new GeminiProvider(properties());
        JsonNode responseBody = new ObjectMapper().readTree("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {"text": "hello"}
                        ]
                      }
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 12,
                    "candidatesTokenCount": 8,
                    "totalTokenCount": 20
                  }
                }
                """);

        LlmResponse response = provider.toLlmResponse(responseBody);

        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getProviderName()).isEqualTo("gemini");
        assertThat(response.getPromptTokens()).isEqualTo(12);
        assertThat(response.getCompletionTokens()).isEqualTo(8);
        assertThat(response.getTotalTokens()).isEqualTo(20);
    }

    @Test
    void leavesTokenUsageNullWhenGeminiDoesNotReportIt() throws Exception {
        GeminiProvider provider = new GeminiProvider(properties());
        JsonNode responseBody = new ObjectMapper().readTree("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {"text": "hello"}
                        ]
                      }
                    }
                  ]
                }
                """);

        LlmResponse response = provider.toLlmResponse(responseBody);

        assertThat(response.getPromptTokens()).isNull();
        assertThat(response.getCompletionTokens()).isNull();
        assertThat(response.getTotalTokens()).isNull();
    }

    private AiGatewayProperties properties() {
        AiGatewayProperties.ProviderConfig config = new AiGatewayProperties.ProviderConfig();
        config.setBaseUrl("http://localhost");
        config.setApiKeyEnv("TEST_GEMINI_API_KEY");
        config.setEnabled(true);

        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setProviders(Map.of(LlmProviderEnum.GEMINI, config));
        return properties;
    }
}
