package org.frostnova.aigateway.conversation.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.conversation.model.ConversationItem;

import java.util.List;

public interface ConversationItemMapper {

    int insert(ConversationItem item);

    ConversationItem findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    List<ConversationItem> findByConversationIdAndUserId(
            @Param("conversationId") String conversationId,
            @Param("userId") Long userId
    );

    Integer findLastSequenceNo(
            @Param("conversationId") String conversationId,
            @Param("userId") Long userId
    );
}
