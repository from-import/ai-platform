package org.frostnova.aigateway.provider;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;
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
                throw new BaseException(
                        ErrorCodes.GATEWAY_CONFIGURATION_ERROR,
                        "Duplicate provider registered: " + provider.getProviderCode().getCode(),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        }
        this.providers = Map.copyOf(registeredProviders);
    }

    public LlmProvider getProvider(LlmProviderEnum providerCode) {
        LlmProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new BaseException(
                    ErrorCodes.PROVIDER_UNAVAILABLE,
                    "Provider is unavailable: " + providerCode.getCode(),
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return provider;
    }
}
