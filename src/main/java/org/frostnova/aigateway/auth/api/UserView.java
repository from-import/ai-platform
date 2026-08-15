package org.frostnova.aigateway.auth.api;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.UserAccount;
import org.frostnova.aigateway.auth.model.UserRole;

public record UserView(Long id, String username, String displayName, UserRole role) {

    public static UserView from(UserAccount user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    public static UserView from(AuthPrincipal principal) {
        return new UserView(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRole()
        );
    }
}
