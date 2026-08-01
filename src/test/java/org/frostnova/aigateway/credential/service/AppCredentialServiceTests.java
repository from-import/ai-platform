package org.frostnova.aigateway.credential.service;

import org.frostnova.aigateway.credential.mapper.AppCredentialMapper;
import org.frostnova.aigateway.credential.model.AppCredential;
import org.frostnova.aigateway.credential.model.AppCredentialStatus;
import org.frostnova.aigateway.credential.model.IssuedAppCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AppCredentialServiceTests {

    @Autowired
    private AppCredentialService credentialService;

    @Autowired
    private AppCredentialMapper credentialMapper;

    @Test
    void issuesHashesFindsAndRevokesCredential() {
        IssuedAppCredential issued = credentialService.issueCredential(
                "portfolio-demo",
                30,
                100_000,
                "[\"gemini/gemini-flash-latest\",\"groq/llama-3.3-70b-versatile\"]"
        );

        AppCredential stored = credentialMapper.findByAppId(issued.appId());
        assertThat(stored).isNotNull();
        assertThat(stored.getApiKeyHash())
                .hasSize(64)
                .isNotEqualTo(issued.apiKey());
        assertThat(stored.getStatus()).isEqualTo(AppCredentialStatus.ACTIVE);
        assertThat(stored.getAllowedModels())
                .isEqualTo("[\"gemini/gemini-flash-latest\",\"groq/llama-3.3-70b-versatile\"]");

        Optional<AppCredential> authenticated = credentialService.findActiveByApiKey(issued.apiKey());
        assertThat(authenticated).isPresent();
        assertThat(authenticated.orElseThrow().getAppId()).isEqualTo(issued.appId());

        assertThat(credentialService.changeStatus(
                issued.appId(),
                AppCredentialStatus.REVOKED
        )).isTrue();
        assertThat(credentialService.findActiveByApiKey(issued.apiKey())).isEmpty();
    }
}
