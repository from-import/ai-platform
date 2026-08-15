package org.frostnova.aigateway.auth.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorTests {

    @Test
    void extractsBearerToken() {
        assertThat(AuthInterceptor.bearerToken("Bearer session-token"))
                .isEqualTo("session-token");
        assertThat(AuthInterceptor.bearerToken("bearer second-token"))
                .isEqualTo("second-token");
        assertThat(AuthInterceptor.bearerToken("Basic credentials")).isNull();
        assertThat(AuthInterceptor.bearerToken("Bearer   ")).isNull();
        assertThat(AuthInterceptor.bearerToken(null)).isNull();
    }
}
