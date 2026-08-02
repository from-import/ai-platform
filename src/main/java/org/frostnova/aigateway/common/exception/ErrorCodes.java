package org.frostnova.aigateway.common.exception;

public final class ErrorCodes {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String UNSUPPORTED_PROVIDER = "UNSUPPORTED_PROVIDER";
    public static final String UNSUPPORTED_MODEL = "UNSUPPORTED_MODEL";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String GATEWAY_CONFIGURATION_ERROR = "GATEWAY_CONFIGURATION_ERROR";
    public static final String LLM_PROVIDER_ERROR = "LLM_PROVIDER_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private ErrorCodes() {
    }
}
