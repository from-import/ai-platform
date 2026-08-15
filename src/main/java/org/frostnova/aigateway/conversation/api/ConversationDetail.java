package org.frostnova.aigateway.conversation.api;

import java.util.List;

public record ConversationDetail(
        ConversationSummary conversation,
        List<ConversationItemView> items
) {
}
