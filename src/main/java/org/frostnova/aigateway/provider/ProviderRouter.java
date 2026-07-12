package org.frostnova.aigateway.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责根据策略选出当前应该用哪个模型
 */
@Component
public class ProviderRouter {
    private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();

    public ProviderRouter(List<LlmProvider> providerList) {
        for (LlmProvider p : providerList) {
            providers.put(p.getProviderCode(), p);
        }
    }

    public LlmProvider getProvider(String targetProvider) {
        return providers.getOrDefault(targetProvider, providers.get(LlmProviderEnum.DEEPSEEK.getCode()));
    }
}
