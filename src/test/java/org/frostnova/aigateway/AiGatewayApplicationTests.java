package org.frostnova.aigateway;

import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AiGatewayApplicationTests {

    @Autowired
    private ProviderRegistry providerRegistry;

    @Test
    void contextLoads() {
        assertThat(providerRegistry.getProvider(LlmProviderEnum.GEMINI)).isNotNull();
        assertThat(providerRegistry.getProvider(LlmProviderEnum.GROQ)).isNotNull();
    }
}
