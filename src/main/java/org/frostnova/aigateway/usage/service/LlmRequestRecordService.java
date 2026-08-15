package org.frostnova.aigateway.usage.service;

import lombok.extern.slf4j.Slf4j;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.usage.mapper.LlmRequestRecordMapper;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestRecordContext;
import org.frostnova.aigateway.usage.model.LlmRequestRecordPage;
import org.frostnova.aigateway.usage.model.LlmRequestRecordQuery;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.frostnova.aigateway.usage.model.UsageStatistics;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class LlmRequestRecordService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    private final LlmRequestRecordMapper mapper;

    public LlmRequestRecordService(LlmRequestRecordMapper mapper) {
        this.mapper = mapper;
    }

    public UsageStatistics getStatistics(AuthPrincipal principal) {
        return mapper.getStatistics(visibleUserId(principal));
    }

    public LlmRequestRecordPage getRequestRecords(
            AuthPrincipal principal,
            String requestId,
            String provider,
            String model,
            LlmRequestStatus status,
            LocalDateTime requestedFrom,
            LocalDateTime requestedTo,
            int page,
            int pageSize
    ) {
        if (page < 1) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "page must be at least 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "pageSize must be between 1 and 100");
        }
        if (requestedFrom != null && requestedTo != null && requestedFrom.isAfter(requestedTo)) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "requestedFrom must not be after requestedTo"
            );
        }

        long offset = (long) (page - 1) * pageSize;
        LlmRequestRecordQuery query = new LlmRequestRecordQuery(
                visibleUserId(principal),
                normalize(requestId),
                normalize(provider),
                normalize(model),
                status,
                requestedFrom,
                requestedTo,
                offset,
                pageSize
        );
        long totalItems = mapper.count(query);
        long totalPages = totalItems == 0 ? 0 : (totalItems + pageSize - 1) / pageSize;

        return new LlmRequestRecordPage(
                mapper.findPage(query),
                page,
                pageSize,
                totalItems,
                totalPages
        );
    }

    public void recordSuccess(
            LlmRequestRecordContext context,
            LlmResponse response
    ) {
        LlmRequestRecord record = baseRecord(context, LlmRequestStatus.SUCCESS);
        record.setPromptTokens(response.getPromptTokens());
        record.setCompletionTokens(response.getCompletionTokens());
        record.setTotalTokens(response.getTotalTokens());
        insertSafely(record);
    }

    public void recordFailure(
            LlmRequestRecordContext context,
            RuntimeException exception
    ) {
        LlmRequestRecord record = baseRecord(context, LlmRequestStatus.FAILED);
        record.setUpstreamStatusCode(upstreamStatusCode(exception));
        record.setErrorCode(errorCode(exception));
        record.setErrorMessage(truncate(exception.getMessage()));
        insertSafely(record);
    }

    private LlmRequestRecord baseRecord(
            LlmRequestRecordContext context,
            LlmRequestStatus status
    ) {
        return LlmRequestRecord.builder()
                .userId(Objects.requireNonNull(context.getUserId(), "userId must not be null"))
                .requestId(context.getRequestId())
                .provider(context.getProvider())
                .model(context.getModel())
                .resultStatus(status)
                .latencyMs(context.latencyMs())
                .requestedAt(context.getRequestedAt())
                .build();
    }

    private Long visibleUserId(AuthPrincipal principal) {
        if (principal.getRole().canViewAllUsage()) {
            return null;
        }
        return principal.getUserId();
    }

    private Integer upstreamStatusCode(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof RestClientResponseException responseException) {
                return responseException.getStatusCode().value();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof BaseException baseException) {
            return baseException.getCode();
        }
        if (exception instanceof RestClientException) {
            return "PROVIDER_ERROR";
        }
        if (exception instanceof IllegalStateException) {
            return "GATEWAY_CONFIGURATION_ERROR";
        }
        return "INTERNAL_ERROR";
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void insertSafely(LlmRequestRecord record) {
        try {
            mapper.insert(record);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to persist LLM request record: requestId={}",
                    record.getRequestId(),
                    exception
            );
        }
    }
}
