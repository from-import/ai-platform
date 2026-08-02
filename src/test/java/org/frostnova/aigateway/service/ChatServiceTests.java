package org.frostnova.aigateway.service;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.provider.LlmProvider;
import org.frostnova.aigateway.provider.LlmProviderEnum;
import org.frostnova.aigateway.provider.ProviderRegistry;
import org.frostnova.aigateway.usage.mapper.LlmRequestRecordMapper;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestRecordQuery;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.frostnova.aigateway.usage.model.UsageStatistics;
import org.frostnova.aigateway.usage.service.LlmRequestRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTests {

    private AiGatewayProperties properties;
    private CapturingProvider geminiProvider;
    private CapturingRequestRecordMapper requestRecordMapper;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        properties = new AiGatewayProperties();
        AiGatewayProperties.ProviderConfig geminiConfig = new AiGatewayProperties.ProviderConfig();
        geminiConfig.setEnabled(true);
        geminiConfig.setSupportedModels(Set.of("test-model"));
        properties.setProviders(Map.of(LlmProviderEnum.GEMINI, geminiConfig));

        geminiProvider = new CapturingProvider(LlmProviderEnum.GEMINI);
        requestRecordMapper = new CapturingRequestRecordMapper();
        chatService = new ChatService(
                new ProviderRegistry(List.of(geminiProvider)),
                properties,
                new LlmRequestRecordService(requestRecordMapper)
        );
    }

    @Test
    void routesExplicitProviderAndModel() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("hello");

        LlmResponse response = chatService.executeChat(request);

        assertThat(response.getContent()).isEqualTo("ok");
        assertThat(geminiProvider.lastRequest.getModel()).isEqualTo("test-model");
        assertThat(geminiProvider.lastRequest.getMessages())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getRole()).isEqualTo("user");
                    assertThat(message.getContent()).isEqualTo("hello");
                });
        assertThat(requestRecordMapper.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getRequestId()).isNotBlank();
                    assertThat(record.getProvider()).isEqualTo("gemini");
                    assertThat(record.getModel()).isEqualTo("test-model");
                    assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.SUCCESS);
                    assertThat(record.getRequestedAt()).isNotNull();
                });
    }

    @Test
    void recordsProviderFailureAndRethrowsIt() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        RuntimeException failure = new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Provider unavailable",
                HttpStatus.BAD_GATEWAY
        );
        geminiProvider.failure = failure;

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isSameAs(failure);

        assertThat(requestRecordMapper.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.FAILED);
                    assertThat(record.getErrorCode()).isEqualTo(ErrorCodes.LLM_PROVIDER_ERROR);
                    assertThat(record.getErrorMessage()).isEqualTo("Provider unavailable");
                });
    }

    @Test
    void rejectsModelThatIsNotInAllowlist() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("unknown-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCodes.UNSUPPORTED_MODEL);
                    assertThat(exception.getMessage())
                            .isEqualTo("Unsupported model for provider gemini: unknown-model");
                });
        assertThat(requestRecordMapper.records).isEmpty();
    }

    @Test
    void rejectsBlankModel() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel(" ");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(BaseException.class)
                .hasMessage("Model must not be blank");
    }

    @Test
    void rejectsUnknownProvider() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("unknown");
        request.setModel("test-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(BaseException.class)
                .hasMessage("Unsupported provider: unknown");
    }

    @Test
    void rejectsBlankProvider() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("test-model");

        assertThatThrownBy(() -> chatService.executeChat(request))
                .isInstanceOf(BaseException.class)
                .hasMessage("Provider must not be blank");
    }

    @Test
    void rejectsDuplicateProviderRegistration() {
        LlmProvider duplicate = new CapturingProvider(LlmProviderEnum.GEMINI);

        assertThatThrownBy(() -> new ProviderRegistry(List.of(geminiProvider, duplicate)))
                .isInstanceOf(BaseException.class)
                .hasMessage("Duplicate provider registered: gemini");
    }

    private static final class CapturingProvider implements LlmProvider {

        private final LlmProviderEnum providerCode;
        private LlmRequest lastRequest;
        private RuntimeException failure;

        private CapturingProvider(LlmProviderEnum providerCode) {
            this.providerCode = providerCode;
        }

        @Override
        public LlmProviderEnum getProviderCode() {
            return providerCode;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            LlmResponse response = new LlmResponse();
            response.setContent("ok");
            response.setProviderName(providerCode.getCode());
            return response;
        }
    }

    private static final class CapturingRequestRecordMapper implements LlmRequestRecordMapper {

        private final List<LlmRequestRecord> records = new ArrayList<>();

        @Override
        public int insert(LlmRequestRecord record) {
            records.add(record);
            return 1;
        }

        @Override
        public LlmRequestRecord findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmRequestRecord findByRequestId(String requestId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LlmRequestRecord> findAll() {
            return List.copyOf(records);
        }

        @Override
        public List<LlmRequestRecord> findPage(LlmRequestRecordQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count(LlmRequestRecordQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UsageStatistics getStatistics() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(LlmRequestRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteById(Long id) {
            throw new UnsupportedOperationException();
        }
    }
}
