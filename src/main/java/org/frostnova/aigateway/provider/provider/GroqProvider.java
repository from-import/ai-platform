package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
public class GroqProvider implements LlmProvider {

    private final RestClient restClient;
    private final AiGatewayProperties.ModelEndpoint endpoint;

    public GroqProvider(AiGatewayProperties properties) {
        this.endpoint = properties.getEndpoints().stream()
                .filter(item -> LlmProviderEnum.GROQ.equals(item.getProviderCode()))
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No enabled Groq endpoint configured"));
        this.restClient = RestClient.builder()
                .baseUrl(endpoint.getBaseUrl())
                .build();
    }

    @Override
    public String getProviderCode() {
        return LlmProviderEnum.GROQ.getCode();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String apiKey = System.getenv(endpoint.getApiKeyEnv());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + endpoint.getApiKeyEnv());
        }

        JsonNode responseBody = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(Map.of(
                        "model", request.getModel() == null ? endpoint.getModel() : request.getModel(),
                        "messages", request.getMessages()
                ))
                .retrieve()
                .body(JsonNode.class);

        LlmResponse response = new LlmResponse();
        response.setContent(extractText(responseBody));
        response.setProviderName(getProviderCode());
        if (responseBody != null) {
            response.setPromptTokens(responseBody.path("usage").path("prompt_tokens").asInt(0));
            response.setCompletionTokens(responseBody.path("usage").path("completion_tokens").asInt(0));
        }
        return response;
    }

    private String extractText(JsonNode responseBody) {
        if (responseBody == null) {
            return "";
        }
        JsonNode textNode = responseBody.path("choices")
                .path(0)
                .path("message")
                .path("content");
        return textNode.isMissingNode() ? "" : textNode.asText();
    }
}
