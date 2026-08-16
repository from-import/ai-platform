package org.frostnova.aigateway.provider.provider;

import lombok.extern.slf4j.Slf4j;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "ai.gateway.providers.groq",
        name = "enabled",
        havingValue = "true"
)
public class GroqProvider implements LlmProvider {

    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKeyEnv;

    public GroqProvider(AiGatewayProperties properties) {
        AiGatewayProperties.ProviderConfig config = properties.requireProvider(LlmProviderEnum.GROQ);
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
        return LlmProviderEnum.GROQ;
    }

    @Override
    public LlmResponse chat(String requestId, LlmRequest request) {
        String apiKey = requireApiKey();

        JsonNode responseBody;
        try {
            log.debug("Calling Groq provider");
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

    @Override
    public Flux<LlmResponse> streamChat(String requestId, LlmRequest request) {
        String apiKey = requireApiKey();
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(Map.of(
                        "model", request.getModel(),
                        "messages", request.getMessages(),
                        "stream", true,
                        "stream_options", Map.of("include_usage", true)
                ))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .takeUntil(event -> isDone(event.data()))
                .filter(event -> event.data() != null
                        && !event.data().isBlank()
                        && !isDone(event.data()))
                .map(event -> toStreamResponse(event.data()))
                .onErrorMap(this::streamProviderException);
    }

    LlmResponse toStreamResponse(String data) {
        try {
            JsonNode responseBody = objectMapper.readTree(data);
            LlmResponse response = new LlmResponse();
            response.setProviderName(getProviderCode().getCode());
            JsonNode content = responseBody.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content");
            response.setContent(content.isMissingNode() || content.isNull() ? "" : content.asText());
            JsonNode usage = responseBody.path("usage");
            response.setPromptTokens(readInteger(usage, "prompt_tokens"));
            response.setCompletionTokens(readInteger(usage, "completion_tokens"));
            response.setTotalTokens(readInteger(usage, "total_tokens"));
            return response;
        } catch (JacksonException exception) {
            throw providerException(null, exception);
        }
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

    private boolean isDone(String data) {
        return data != null && "[DONE]".equals(data.strip());
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
