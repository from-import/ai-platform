package org.frostnova.aigateway.domain.model;

import lombok.Data;

/**
 * 模型返回的标准结构
 */
@Data
public class LlmResponse {
    /**
     * 返回文本
     */
    private String content;

    /**
     * input token消耗
     */
    private Integer promptTokens;

    /**
     * output token消耗
     */
    private Integer completionTokens;

    /**
     * Provider 报告的总 Token 消耗
     */
    private Integer totalTokens;

    /**
     * 模型回答提供方name
     */
    private String providerName;
}
