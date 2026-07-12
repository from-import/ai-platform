package org.frostnova.aigateway.provider.provider;

import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.springframework.stereotype.Component;

@Component
public class DeepseekProvider implements LlmProvider {
    @Override
    public String getProviderCode() { return LlmProviderEnum.DEEPSEEK.getCode(); }

    @Override
    public LlmResponse chat(LlmRequest request) {
        // TODO: 组装 HTTP 请求发给 DeepSeek API
        return new LlmResponse();
    }
}
