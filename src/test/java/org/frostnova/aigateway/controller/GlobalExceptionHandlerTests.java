package org.frostnova.aigateway.controller;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsStableCodeAndMessageForBaseException() {
        BaseException exception = new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Gemini request failed with HTTP 429",
                HttpStatus.BAD_GATEWAY
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleBaseException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Gemini request failed with HTTP 429"
        ));
    }
}
