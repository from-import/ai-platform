package org.frostnova.aigateway.usage.model;

import java.util.List;

public record LlmRequestRecordPage(
        List<LlmRequestRecord> items,
        int page,
        int pageSize,
        long totalItems,
        long totalPages
) {

    public LlmRequestRecordPage {
        items = List.copyOf(items);
    }
}
