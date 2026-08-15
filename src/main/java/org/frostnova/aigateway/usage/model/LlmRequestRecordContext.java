package org.frostnova.aigateway.usage.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Getter
@Builder
public class LlmRequestRecordContext {

    private final Long userId;
    private final String requestId;
    private final String provider;
    private final String model;
    private final LocalDateTime requestedAt;
    private final long startedAtNanos;
    private long durationNanos;

    public long latencyMs() {
        return TimeUnit.NANOSECONDS.toMillis(durationNanos);
    }

    public void recordEndTime() {
        durationNanos = System.nanoTime() - startedAtNanos;
    }
}
