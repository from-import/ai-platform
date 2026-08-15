package org.frostnova.aigateway.auth.controller;

import org.frostnova.aigateway.usage.mapper.LlmRequestRecordMapper;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthHttpFlowTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LlmRequestRecordMapper requestRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void protectsApisAndSupportsTheCompleteSessionFlow() throws Exception {
        String username = "http-user-" + UUID.randomUUID();

        HttpResponse<String> browserNavigation = send(
                "GET",
                "/api/v1/models",
                null,
                null,
                "text/html"
        );
        assertThat(browserNavigation.statusCode()).isEqualTo(302);
        assertThat(browserNavigation.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/#/login"));

        HttpResponse<String> unauthorized = send("GET", "/api/v1/models", null, null);
        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(unauthorized.body()).path("code").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");

        HttpResponse<String> registration = send(
                "POST",
                "/api/v1/auth/register",
                """
                        {"username":"%s","password":"http-password","displayName":"HTTP User"}
                        """.formatted(username),
                null
        );
        assertThat(registration.statusCode()).isEqualTo(201);

        HttpResponse<String> login = send(
                "POST",
                "/api/v1/auth/login",
                """
                        {"username":"%s","password":"http-password"}
                        """.formatted(username),
                null
        );
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = objectMapper.readTree(login.body());
        String token = loginBody.path("token").asText();
        assertThat(token).isNotBlank();
        assertThat(loginBody.path("expiresAt").asText()).endsWith("Z");
        assertThat(loginBody.path("user").path("role").asText()).isEqualTo("USER");

        HttpResponse<String> currentUser = send("GET", "/api/v1/auth/me", null, token);
        assertThat(currentUser.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(currentUser.body()).path("username").asText())
                .isEqualTo(username);
        assertThat(objectMapper.readTree(currentUser.body()).path("role").asText())
                .isEqualTo("USER");

        HttpResponse<String> logout = send("POST", "/api/v1/auth/logout", null, token);
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> revoked = send("GET", "/api/v1/auth/me", null, token);
        assertThat(revoked.statusCode()).isEqualTo(401);
    }

    @Test
    void scopesUsageToUsersAndAllowsAdministratorsToViewAll() throws Exception {
        LoginSession firstUser = registerAndLogin("usage-http-one-" + UUID.randomUUID());
        LoginSession secondUser = registerAndLogin("usage-http-two-" + UUID.randomUUID());

        insertUsage(firstUser.userId(), "first-user-request-" + UUID.randomUUID(), 10);
        insertUsage(secondUser.userId(), "second-user-request-" + UUID.randomUUID(), 20);

        JsonNode userStatistics = objectMapper.readTree(send(
                "GET",
                "/api/v1/usage/statistics",
                null,
                firstUser.token()
        ).body());
        assertThat(userStatistics.path("totalRequests").asLong()).isEqualTo(1);
        assertThat(userStatistics.path("totalTokens").asLong()).isEqualTo(10);

        assertThat(jdbcTemplate.update(
                "UPDATE app_user SET role = 'ADMIN' WHERE id = ?",
                firstUser.userId()
        )).isEqualTo(1);

        JsonNode adminStatistics = objectMapper.readTree(send(
                "GET",
                "/api/v1/usage/statistics",
                null,
                firstUser.token()
        ).body());
        assertThat(adminStatistics.path("totalRequests").asLong()).isEqualTo(2);
        assertThat(adminStatistics.path("totalTokens").asLong()).isEqualTo(30);
    }

    private LoginSession registerAndLogin(String username) throws Exception {
        HttpResponse<String> registration = send(
                "POST",
                "/api/v1/auth/register",
                """
                        {"username":"%s","password":"http-password","displayName":"Usage User"}
                        """.formatted(username),
                null
        );
        assertThat(registration.statusCode()).isEqualTo(201);
        long userId = objectMapper.readTree(registration.body()).path("id").asLong();

        HttpResponse<String> login = send(
                "POST",
                "/api/v1/auth/login",
                """
                        {"username":"%s","password":"http-password"}
                        """.formatted(username),
                null
        );
        assertThat(login.statusCode()).isEqualTo(200);
        String token = objectMapper.readTree(login.body()).path("token").asText();
        return new LoginSession(userId, token);
    }

    private void insertUsage(Long userId, String requestId, int totalTokens) {
        LlmRequestRecord record = LlmRequestRecord.builder()
                .userId(userId)
                .requestId(requestId)
                .provider("gemini")
                .model("gemini-flash-latest")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .totalTokens(totalTokens)
                .latencyMs(100L)
                .requestedAt(LocalDateTime.now())
                .build();
        assertThat(requestRecordMapper.insert(record)).isEqualTo(1);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            String token
    ) throws Exception {
        return send(method, path, body, token, null);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            String token,
            String accept
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (accept != null) {
            builder.header("Accept", accept);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record LoginSession(Long userId, String token) {
    }
}
