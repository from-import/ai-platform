package org.frostnova.aigateway.provider.provider;

import lombok.extern.slf4j.Slf4j;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.domain.model.Message;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "ai.gateway.providers.gemini",
        name = "enabled",
        havingValue = "true"
)
public class GeminiProvider implements LlmProvider {

    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKeyEnv;

    public GeminiProvider(AiGatewayProperties properties) {
        AiGatewayProperties.ProviderConfig config = properties.requireProvider(LlmProviderEnum.GEMINI);
        this.apiKeyEnv = config.getApiKeyEnv();
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmProviderEnum getProviderCode() {
        return LlmProviderEnum.GEMINI;
    }

    @Override
    public LlmResponse chat(String requestId, LlmRequest request) {
        String apiKey = requireApiKey();

        JsonNode responseBody;
        try {
            log.debug("Calling Gemini provider");
            responseBody = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", request.getModel())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-goog-api-key", apiKey)
                    .body(buildGeminiRequest(request))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw providerException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw providerException(null, exception);
        }

        return toLlmResponse(responseBody);
    }

    @Override
    public Flux<LlmResponse> streamChat(String requestId, LlmRequest request) {
        String apiKey = requireApiKey();
        return webClient.post()
                .uri(
                        "/v1beta/models/{model}:streamGenerateContent?alt=sse",
                        request.getModel()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-goog-api-key", apiKey)
                .bodyValue(buildGeminiRequest(request))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .filter(event -> event.data() != null && !event.data().isBlank())
                .map(event -> toStreamResponse(event.data()))
                .onErrorMap(this::streamProviderException);
    }

    private LlmResponse toStreamResponse(String data) {
        try {
            return toLlmResponse(objectMapper.readTree(data));
        } catch (JacksonException exception) {
            throw providerException(null, exception);
        }
    }

    LlmResponse toLlmResponse(JsonNode responseBody) {
        LlmResponse response = new LlmResponse();
        response.setContent(extractText(responseBody));
        response.setProviderName(getProviderCode().getCode());
        if (responseBody != null) {
            JsonNode usage = responseBody.path("usageMetadata");
            response.setPromptTokens(readInteger(usage, "promptTokenCount"));
            response.setCompletionTokens(readInteger(usage, "candidatesTokenCount"));
            response.setTotalTokens(readInteger(usage, "totalTokenCount"));
        }
        return response;
    }

    private Integer readInteger(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        return value.isIntegralNumber() ? value.intValue() : null;
    }

    private String requireApiKey() {
        String apiKey = System.getenv(apiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            throw new BaseException(
                    ErrorCodes.GATEWAY_CONFIGURATION_ERROR,
                    "Missing environment variable: " + apiKeyEnv,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return apiKey;
    }

    private RuntimeException streamProviderException(Throwable exception) {
        if (exception instanceof BaseException baseException) {
            return baseException;
        }
        if (exception instanceof WebClientResponseException responseException) {
            return providerException(responseException.getStatusCode().value(), responseException);
        }
        if (exception instanceof WebClientException webClientException) {
            return providerException(null, webClientException);
        }
        return providerException(null, exception);
    }

    private BaseException providerException(Integer statusCode, Throwable cause) {
        String statusMessage = statusCode == null ? "" : " with HTTP " + statusCode;
        return new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Gemini request failed" + statusMessage,
                HttpStatus.BAD_GATEWAY,
                cause
        );
    }

    Map<String, Object> buildGeminiRequest(LlmRequest request) {
        List<Map<String, Object>> contents = request.getMessages()
                .stream()
                .filter(this::hasTextContent)
                .map(this::toGeminiContent)
                .toList();
        return Map.of("contents", contents);
    }

    private boolean hasTextContent(Message message) {
        return message.getContent() != null && !message.getContent().isBlank();
    }

    private Map<String, Object> toGeminiContent(Message message) {
        return Map.of(
                "role", toGeminiRole(message.getRole()),
                "parts", List.of(Map.of("text", message.getContent()))
        );
    }

    private String toGeminiRole(String role) {
        String normalizedRole = role == null ? "" : role.strip().toLowerCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "user" -> "user";
            case "assistant", "model" -> "model";
            default -> throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "Unsupported Gemini message role: " + role
            );
        };
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
