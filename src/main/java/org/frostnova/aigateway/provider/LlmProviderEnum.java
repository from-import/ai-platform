package org.frostnova.aigateway.provider;

import lombok.Getter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Getter
public enum LlmProviderEnum {

    GEMINI("gemini"),
    GROQ("groq"),
    ;

    private final String code;

    LlmProviderEnum(String code) {
        this.code = code;
    }

    private static final Map<String, LlmProviderEnum> codeMap;
    static {
        codeMap = new HashMap<>();
        for (LlmProviderEnum providerEnum : LlmProviderEnum.values()) {
            codeMap.put(providerEnum.code, providerEnum);
        }
    }

    public static LlmProviderEnum getProviderByCode(String code) {
        if (code == null) {
            return null;
        }
        return codeMap.get(code.toLowerCase(Locale.ROOT));
    }
}
