package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "ai.gateway.providers.groq",
        name = "enabled",
        havingValue = "true"
)
public class GroqProvider implements LlmProvider {

    private final RestClient restClient;
    private final String apiKeyEnv;

    public GroqProvider(AiGatewayProperties properties) {
        AiGatewayProperties.ProviderConfig config = properties.requireProvider(LlmProviderEnum.GROQ);
        this.apiKeyEnv = config.getApiKeyEnv();
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Override
    public LlmProviderEnum getProviderCode() {
        return LlmProviderEnum.GROQ;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String apiKey = System.getenv(apiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            throw new BaseException(
                    ErrorCodes.GATEWAY_CONFIGURATION_ERROR,
                    "Missing environment variable: " + apiKeyEnv,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        JsonNode responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(Map.of(
                            "model", request.getModel(),
                            "messages", request.getMessages()
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw providerException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw providerException(null, exception);
        }

        LlmResponse response = new LlmResponse();
        response.setContent(extractText(responseBody));
        response.setProviderName(getProviderCode().getCode());
        if (responseBody != null) {
            JsonNode usage = responseBody.path("usage");
            response.setPromptTokens(readInteger(usage, "prompt_tokens"));
            response.setCompletionTokens(readInteger(usage, "completion_tokens"));
            response.setTotalTokens(readInteger(usage, "total_tokens"));
        }
        return response;
    }

    private Integer readInteger(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        return value.isIntegralNumber() ? value.intValue() : null;
    }

    private BaseException providerException(Integer statusCode, RestClientException cause) {
        String statusMessage = statusCode == null ? "" : " with HTTP " + statusCode;
        return new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Groq request failed" + statusMessage,
                HttpStatus.BAD_GATEWAY,
                cause
        );
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
