package org.frostnova.aigateway.conversation.api;

import jakarta.validation.constraints.Size;

public record MoveConversationRequest(
        @Size(max = 36)
        String projectId
) {
}
