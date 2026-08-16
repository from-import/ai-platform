package org.frostnova.aigateway.conversation.api;

import org.frostnova.aigateway.conversation.model.ChatProject;

import java.time.LocalDateTime;

public record ProjectView(
        String id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ProjectView from(ChatProject project) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
