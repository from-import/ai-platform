package org.frostnova.aigateway.credential.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.credential.model.AppCredential;
import org.frostnova.aigateway.credential.model.AppCredentialStatus;

public interface AppCredentialMapper {

    int insert(AppCredential credential);

    AppCredential findByAppId(@Param("appId") String appId);

    AppCredential findActiveByApiKeyHash(@Param("apiKeyHash") String apiKeyHash);

    int updateStatus(
            @Param("appId") String appId,
            @Param("status") AppCredentialStatus status
    );
}
