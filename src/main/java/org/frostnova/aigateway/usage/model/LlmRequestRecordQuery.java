package org.frostnova.aigateway.usage.model;

import java.time.LocalDateTime;

public record LlmRequestRecordQuery(
        Long userId,
        String requestId,
        String provider,
        String model,
        LlmRequestStatus status,
        LocalDateTime requestedFrom,
        LocalDateTime requestedTo,
        long offset,
        int limit
) {
}
