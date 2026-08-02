package org.frostnova.aigateway.usage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatistics {

    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Double averageLatencyMs;
}
