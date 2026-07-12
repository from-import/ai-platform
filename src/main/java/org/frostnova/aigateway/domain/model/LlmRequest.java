package org.frostnova.aigateway.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 中台发给大模型底层的基础请求结构（对齐 OpenAI 标准）
 */
@Data
public class LlmRequest {
    private String model;
    private List<Message> messages = new ArrayList<>();

    public void addMessage(String role, String content) {
        Message message = new Message();
        message.setRole(role);
        message.setContent(content);
        messages.add(message);
    }

}
