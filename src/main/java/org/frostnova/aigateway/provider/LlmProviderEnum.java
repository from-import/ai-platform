package org.frostnova.aigateway.provider;

import lombok.Getter;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;

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

    public static LlmProviderEnum requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "Provider must not be blank");
        }

        LlmProviderEnum provider = codeMap.get(code.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new BaseException(ErrorCodes.UNSUPPORTED_PROVIDER, "Unsupported provider: " + code);
        }
        return provider;
    }
}
