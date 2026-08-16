package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.domain.model.Message;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiProviderTests {

    @Test
    void buildsOneGeminiContentForEachConversationMessage() throws Exception {
        GeminiProvider provider = new GeminiProvider(properties());
        LlmRequest request = new LlmRequest();
        request.setMessages(List.of(
                new Message("user", "first question"),
                new Message("assistant", "first answer"),
                new Message("user", "follow-up question")
        ));
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode requestBody = objectMapper.readTree(
                objectMapper.writeValueAsString(provider.buildGeminiRequest(request))
        );

        JsonNode contents = requestBody.path("contents");
        assertThat(contents.size()).isEqualTo(3);
        assertThat(contents.path(0).path("role").asText()).isEqualTo("user");
        assertThat(contents.path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("first question");
        assertThat(contents.path(1).path("role").asText()).isEqualTo("model");
        assertThat(contents.path(1).path("parts").path(0).path("text").asText())
                .isEqualTo("first answer");
        assertThat(contents.path(2).path("role").asText()).isEqualTo("user");
        assertThat(contents.path(2).path("parts").path(0).path("text").asText())
                .isEqualTo("follow-up question");
    }

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
