package org.frostnova.aigateway.prompt;

import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateManager {

    /**
     * 获取模板并渲染参数
     * 例如：模板是 "判断以下文本是否违规：{{text}}"
     * 传入变量 {"text": "我爱你"}
     * 返回 "判断以下文本是否违规：我爱你"
     */
     public String renderPrompt(String promptId, AppChatRequest request) {
         return "You are a helpful AI assistant.";
     }
}
