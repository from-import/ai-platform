package org.frostnova.aigateway.auth.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.AuthSession;
import org.frostnova.aigateway.auth.model.UserAccount;

import java.time.LocalDateTime;

public interface AuthMapper {

    UserAccount findUserByUsername(@Param("username") String username);

    int insertUser(UserAccount user);

    int insertSession(AuthSession session);

    AuthPrincipal findActivePrincipal(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

    int touchSession(
            @Param("sessionId") Long sessionId,
            @Param("lastUsedAt") LocalDateTime lastUsedAt
    );

    int revokeSession(
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") LocalDateTime revokedAt
    );
}
