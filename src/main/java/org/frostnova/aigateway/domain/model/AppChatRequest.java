package org.frostnova.aigateway.domain.model;

import lombok.Data;

@Data
public class AppChatRequest {
    /**
     * Provider Code，例如 gemini 或 groq
     */
    private String provider;

    /**
     * Provider 上游的具体模型，例如 gemini-flash-latest
     */
    private String model;

    /**
     * 提问文本内容
     */
    private String userMessage;
}
