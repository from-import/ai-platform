package org.frostnova.aigateway;

import org.frostnova.aigateway.config.AiGatewayProperties;
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

    @Autowired
    private AiGatewayProperties properties;

    @Test
    void contextLoads() {
        assertThat(providerRegistry.getProvider(LlmProviderEnum.GEMINI)).isNotNull();
        assertThat(providerRegistry.getProvider(LlmProviderEnum.GROQ)).isNotNull();
        assertThat(properties.requireProvider(LlmProviderEnum.GEMINI).getSupportedModels())
                .containsExactly(
                        "gemini-3.6-flash",
                        "gemini-3.5-flash",
                        "gemini-3.5-flash-lite",
                        "gemini-3.1-pro-preview",
                        "gemini-3.1-flash-lite",
                        "gemini-2.5-pro",
                        "gemini-2.5-flash",
                        "gemini-2.5-flash-lite",
                        "gemini-flash-latest"
                );
        assertThat(properties.requireProvider(LlmProviderEnum.GROQ).getSupportedModels())
                .containsExactly(
                        "llama-3.3-70b-versatile",
                        "llama-3.1-8b-instant",
                        "openai/gpt-oss-120b",
                        "openai/gpt-oss-20b",
                        "groq/compound",
                        "groq/compound-mini",
                        "qwen/qwen3.6-27b"
                );
    }
}
