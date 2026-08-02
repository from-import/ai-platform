package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashSet;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelControllerTests {

    @Test
    void listsConfiguredModelsForEnabledProviders() throws Exception {
        AiGatewayProperties.ProviderConfig gemini = provider(
                true,
                "gemini-flash-latest",
                "gemini-pro"
        );
        AiGatewayProperties.ProviderConfig groq = provider(false, "hidden-model");

        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setProviders(Map.of(
                LlmProviderEnum.GEMINI, gemini,
                LlmProviderEnum.GROQ, groq
        ));

        ModelController controller = new ModelController(properties);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].provider").value("gemini"))
                .andExpect(jsonPath("$[0].model").value("gemini-flash-latest"))
                .andExpect(jsonPath("$[1].provider").value("gemini"))
                .andExpect(jsonPath("$[1].model").value("gemini-pro"));
    }

    private AiGatewayProperties.ProviderConfig provider(boolean enabled, String... models) {
        AiGatewayProperties.ProviderConfig config = new AiGatewayProperties.ProviderConfig();
        config.setEnabled(enabled);
        config.setSupportedModels(new LinkedHashSet<>(java.util.List.of(models)));
        return config;
    }
}
