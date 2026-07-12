package org.frostnova.aigateway.domain.model;

import lombok.Data;

import java.util.Map;

@Data
public class AppChatRequest {
    /**
     * 标识是哪个业务（用于后续鉴权、计费）
     */
    private String appId;

    /**
     * 使用的 Prompt 模板 ID
     */
    private String promptId;

    /**
     * 模型别名，例如 private-chat / general-chat / fast-chat
     */
    private String modelAlias;

    /**
     * 动态替换的参数
     */
    private Map<String, Object> variables;

    /**
     * 提问文本内容
     */
    private String userMessage;
}
