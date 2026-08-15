package org.frostnova.aigateway.conversation.api;

import org.frostnova.aigateway.conversation.model.ConversationItemType;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ConversationItemView(
        Long id,
        Integer sequenceNo,
        ConversationItemType itemType,
        ConversationRole role,
        JsonNode payload,
        LocalDateTime createdAt
) {
}
