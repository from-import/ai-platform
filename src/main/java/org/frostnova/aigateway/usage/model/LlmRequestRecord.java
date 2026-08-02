package org.frostnova.aigateway.usage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequestRecord {

    private Long id;
    private String requestId;
    private String provider;
    private String model;
    private LlmRequestStatus resultStatus;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private Integer upstreamStatusCode;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime requestedAt;
}
