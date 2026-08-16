package org.frostnova.aigateway.provider;

import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import reactor.core.publisher.Flux;

/**
 * 所有大模型厂商必须实现这个接口
 */
public interface LlmProvider {
    /**
     * 厂商唯一标识
     */
    LlmProviderEnum getProviderCode();

    LlmResponse chat(String requestId, LlmRequest request);

    Flux<LlmResponse> streamChat(String requestId, LlmRequest request);
}
