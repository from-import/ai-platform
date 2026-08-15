package org.frostnova.aigateway.conversation.api;

import org.frostnova.aigateway.conversation.model.ChatConversation;

import java.time.LocalDateTime;

public record ConversationSummary(
        String id,
        String projectId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        LocalDateTime updatedAt
) {

    public static ConversationSummary from(ChatConversation conversation) {
        return new ConversationSummary(
                conversation.getId(),
                conversation.getProjectId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                conversation.getUpdatedAt()
        );
    }
}
