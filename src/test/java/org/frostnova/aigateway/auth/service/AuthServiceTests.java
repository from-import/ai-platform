package org.frostnova.aigateway.auth.service;

import org.frostnova.aigateway.auth.api.LoginRequest;
import org.frostnova.aigateway.auth.api.LoginResponse;
import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.UserRole;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AuthServiceTests {

    @Autowired
    private AuthService authService;

    @Test
    void registersLogsInAuthenticatesAndLogsOut() {
        UserView registered = authService.register(new RegisterRequest(
                "Test.User",
                "correct-password",
                "Test User"
        ));

        assertThat(registered.id()).isNotNull();
        assertThat(registered.username()).isEqualTo("test.user");
        assertThat(registered.role()).isEqualTo(UserRole.USER);

        LoginResponse login = authService.login(new LoginRequest(
                "TEST.USER",
                "correct-password"
        ));

        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.token()).isNotBlank();
        assertThat(login.user()).isEqualTo(registered);

        AuthPrincipal principal = authService.authenticate(login.token());
        assertThat(principal.getUserId()).isEqualTo(registered.id());
        assertThat(principal.getUsername()).isEqualTo("test.user");
        assertThat(principal.getRole()).isEqualTo(UserRole.USER);

        authService.logout(login.token());

        assertThatThrownBy(() -> authService.authenticate(login.token()))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCodes.AUTHENTICATION_REQUIRED));
    }

    @Test
    void rejectsInvalidPassword() {
        authService.register(new RegisterRequest(
                "invalid-password-user",
                "correct-password",
                null
        ));

        assertThatThrownBy(() -> authService.login(new LoginRequest(
                "invalid-password-user",
                "wrong-password"
        )))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCodes.INVALID_CREDENTIALS));
    }
}
