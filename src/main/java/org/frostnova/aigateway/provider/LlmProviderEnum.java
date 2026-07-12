package org.frostnova.aigateway.provider;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum LlmProviderEnum {

    OLLAMA("OLLAMA", "本地 Ollama"),
    GEMINI("GEMINI", "Google Gemini"),
    GROQ("GROQ", "Groq"),
    OPENAI("OPENAI", "OpenAI"),
    DEEPSEEK("DEEPSEEK", "深度求索"),
    ;

    private final String code;
    private final String desc;

    LlmProviderEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<String, LlmProviderEnum> codeMap;
    private static final Map<String, LlmProviderEnum> descMap;
    static {
        codeMap = new HashMap<>();
        descMap = new HashMap<>();
        for (LlmProviderEnum providerEnum : LlmProviderEnum.values()) {
            codeMap.put(providerEnum.code, providerEnum);
            descMap.put(providerEnum.desc, providerEnum);
        }
    }

    public static LlmProviderEnum getProviderByCode(String code) {
        return codeMap.get(code);
    }

    public static LlmProviderEnum getProviderByDesc(String desc) {
        return descMap.get(desc);
    }


}
