package org.frostnova.aigateway.domain.model;

import lombok.Data;

@Data
public class AppChatRequest {
    /**
     * 已存在的对话 ID。为空时由服务端创建新对话。
     */
    private String conversationId;

    /**
     * 创建新对话时可指定所属项目。继续已有对话时以数据库归属为准。
     */
    private String projectId;

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
