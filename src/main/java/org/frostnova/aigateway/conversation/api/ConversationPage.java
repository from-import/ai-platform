package org.frostnova.aigateway.conversation.api;

import java.util.List;

public record ConversationPage(
        List<ConversationSummary> items,
        String nextCursor,
        boolean hasMore
) {
}
