package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.domain.model.Message;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "ai.gateway.providers.gemini",
        name = "enabled",
        havingValue = "true"
)
public class GeminiProvider implements LlmProvider {

    private final RestClient restClient;
    private final String apiKeyEnv;

    public GeminiProvider(AiGatewayProperties properties) {
        AiGatewayProperties.ProviderConfig config = properties.requireProvider(LlmProviderEnum.GEMINI);
        this.apiKeyEnv = config.getApiKeyEnv();
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Override
    public LlmProviderEnum getProviderCode() {
        return LlmProviderEnum.GEMINI;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String apiKey = System.getenv(apiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + apiKeyEnv);
        }

        JsonNode responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", request.getModel())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-goog-api-key", apiKey)
                    .body(buildGeminiRequest(request))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw ex;
        }

        LlmResponse response = new LlmResponse();
        response.setContent(extractText(responseBody));
        response.setProviderName(getProviderCode().getCode());
        return response;
    }

    private Map<String, Object> buildGeminiRequest(LlmRequest request) {
        return Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", toGeminiPrompt(request.getMessages())))
                ))
        );
    }

    private String toGeminiPrompt(List<Message> messages) {
        StringBuilder prompt = new StringBuilder();
        for (Message message : messages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            if (!prompt.isEmpty()) {
                prompt.append(System.lineSeparator()).append(System.lineSeparator());
            }
            prompt.append(message.getRole()).append(": ").append(message.getContent());
        }
        return prompt.toString();
    }

    private String extractText(JsonNode responseBody) {
        if (responseBody == null) {
            return "";
        }
        JsonNode textNode = responseBody.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text");
        return textNode.isMissingNode() ? "" : textNode.asText();
    }
}
