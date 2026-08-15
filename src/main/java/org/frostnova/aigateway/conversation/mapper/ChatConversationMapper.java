package org.frostnova.aigateway.conversation.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.conversation.model.ChatConversation;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatConversationMapper {

    int insert(ChatConversation conversation);

    ChatConversation findByIdAndUserId(
            @Param("id") String id,
            @Param("userId") Long userId
    );

    ChatConversation findByIdAndUserIdForUpdate(
            @Param("id") String id,
            @Param("userId") Long userId
    );

    List<ChatConversation> findRecentByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    List<ChatConversation> findPageByUserId(
            @Param("userId") Long userId,
            @Param("cursorLastMessageAt") LocalDateTime cursorLastMessageAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    List<ChatConversation> findRecentByProjectId(
            @Param("userId") Long userId,
            @Param("projectId") String projectId,
            @Param("limit") int limit
    );

    List<ChatConversation> findRecentWithoutProject(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    int updateTitle(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("title") String title
    );

    int updateProject(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("projectId") String projectId
    );

    int updateLastMessageAt(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt
    );

    int deleteByIdAndUserId(
            @Param("id") String id,
            @Param("userId") Long userId
    );
}
