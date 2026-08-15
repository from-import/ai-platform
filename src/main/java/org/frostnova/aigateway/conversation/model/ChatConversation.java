package org.frostnova.aigateway.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {

    private String id;
    private Long userId;
    private String projectId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime updatedAt;
}
