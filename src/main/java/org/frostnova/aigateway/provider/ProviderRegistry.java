package org.frostnova.aigateway.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<LlmProviderEnum, LlmProvider> providers;

    public ProviderRegistry(List<LlmProvider> providerList) {
        Map<LlmProviderEnum, LlmProvider> registeredProviders = new EnumMap<>(LlmProviderEnum.class);
        for (LlmProvider provider : providerList) {
            LlmProvider previous = registeredProviders.put(provider.getProviderCode(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate provider registered: " + provider.getProviderCode().getCode()
                );
            }
        }
        this.providers = Map.copyOf(registeredProviders);
    }

    public LlmProvider getProvider(LlmProviderEnum providerCode) {
        LlmProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerCode.getCode());
        }
        return provider;
    }
}
