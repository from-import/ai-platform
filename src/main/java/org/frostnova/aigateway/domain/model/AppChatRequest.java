package org.frostnova.aigateway.domain.model;

import lombok.Data;

@Data
public class AppChatRequest {
    /**
     * 具体模型，格式为 provider/model，例如 gemini/gemini-flash-latest
     */
    private String model;

    /**
     * 提问文本内容
     */
    private String userMessage;
}
