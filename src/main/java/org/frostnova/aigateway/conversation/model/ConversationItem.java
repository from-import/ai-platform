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
public class ConversationItem {

    private Long id;
    private String conversationId;
    private Integer sequenceNo;
    private ConversationItemType itemType;
    private ConversationRole role;
    private String payload;
    private LocalDateTime createdAt;
}
