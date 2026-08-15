package org.frostnova.aigateway.auth.api;

import java.time.OffsetDateTime;

public record LoginResponse(
        String token,
        String tokenType,
        OffsetDateTime expiresAt,
        UserView user
) {
}
