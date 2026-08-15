package org.frostnova.aigateway.conversation.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.conversation.model.ChatProject;

import java.util.List;

public interface ChatProjectMapper {

    int insert(ChatProject project);

    ChatProject findByIdAndUserId(
            @Param("id") String id,
            @Param("userId") Long userId
    );

    List<ChatProject> findAllByUserId(@Param("userId") Long userId);

    int updateName(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("name") String name
    );

    int deleteByIdAndUserId(
            @Param("id") String id,
            @Param("userId") Long userId
    );
}
