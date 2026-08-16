package org.frostnova.aigateway.service;

import org.frostnova.aigateway.chat.ChatService;
import org.frostnova.aigateway.chat.command.ChatCommand;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.config.AiGatewayProperties;
import org.frostnova.aigateway.conversation.manager.ConversationManager;
import org.frostnova.aigateway.conversation.model.ChatConversation;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.LlmRequest;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.domain.model.Message;
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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTests {

    private static final String REQUEST_ID = "request-test";
    private static final long USER_ID = 42L;

    private AiGatewayProperties properties;
    private CapturingProvider geminiProvider;
    private CapturingRequestRecordMapper requestRecordMapper;
    private CapturingConversationManager conversationManager;
    private ChatConversation conversation;
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
        conversation = ChatConversation.builder()
                .id("conversation-test")
                .userId(USER_ID)
                .title("hello")
                .build();
        conversationManager = new CapturingConversationManager(conversation);
        chatService = new ChatService(
                new ProviderRegistry(List.of(geminiProvider)),
                properties,
                new LlmRequestRecordService(requestRecordMapper),
                conversationManager
        );
    }

    @Test
    void routesExplicitProviderAndModel() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("hello");

        LlmResponse response = chatService.executeChat(command(request));

        assertThat(response.getContent()).isEqualTo("ok");
        assertThat(response.getConversationId()).isEqualTo(conversation.getId());
        assertThat(geminiProvider.lastRequestId).isEqualTo(REQUEST_ID);
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
                    assertThat(record.getRequestId()).isEqualTo(REQUEST_ID);
                    assertThat(record.getUserId()).isEqualTo(USER_ID);
                    assertThat(record.getProvider()).isEqualTo("gemini");
                    assertThat(record.getModel()).isEqualTo("test-model");
                    assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.SUCCESS);
                    assertThat(record.getRequestedAt()).isNotNull();
                    assertThat(record.getLatencyMs()).isBetween(0L, 60_000L);
                });
        assertThat(conversationManager.appendedRoles)
                .containsExactly(ConversationRole.USER, ConversationRole.ASSISTANT);
        assertThat(conversationManager.appendedContents).containsExactly("hello", "ok");
    }

    @Test
    void sendsPersistedConversationHistoryToProvider() {
        conversationManager.history.add(new Message("user", "first question"));
        conversationManager.history.add(new Message("assistant", "first answer"));
        AppChatRequest request = new AppChatRequest();
        request.setConversationId(conversation.getId());
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("follow-up question");

        chatService.executeChat(command(request));

        assertThat(geminiProvider.lastRequest.getMessages())
                .containsExactly(
                        new Message("user", "first question"),
                        new Message("assistant", "first answer"),
                        new Message("user", "follow-up question")
                );
    }

    @Test
    void streamsChunksAndPersistsOneCompleteAssistantMessage() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("hello");
        geminiProvider.streamResponses = List.of(
                streamResponse("hel", null, null, null),
                streamResponse("lo", 3, 2, 5)
        );

        List<LlmResponse> responses = chatService.executeChatStream(command(request))
                .collectList()
                .block();

        assertThat(responses).isNotNull();
        assertThat(responses).extracting(LlmResponse::getContent)
                .containsExactly("", "hel", "lo");
        assertThat(responses).allSatisfy(response ->
                assertThat(response.getConversationId()).isEqualTo(conversation.getId()));
        assertThat(conversationManager.appendedRoles)
                .containsExactly(ConversationRole.USER, ConversationRole.ASSISTANT);
        assertThat(conversationManager.appendedContents).containsExactly("hello", "hello");
        assertThat(requestRecordMapper.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.SUCCESS);
                    assertThat(record.getPromptTokens()).isEqualTo(3);
                    assertThat(record.getCompletionTokens()).isEqualTo(2);
                    assertThat(record.getTotalTokens()).isEqualTo(5);
                });
    }

    @Test
    void recordsStreamingProviderFailureWithoutPersistingAssistantMessage() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("hello");
        geminiProvider.failure = new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Provider unavailable",
                HttpStatus.BAD_GATEWAY
        );

        assertThatThrownBy(() -> chatService.executeChatStream(command(request)).blockLast())
                .isInstanceOf(BaseException.class)
                .hasMessage("Provider unavailable");

        assertThat(conversationManager.appendedRoles).containsExactly(ConversationRole.USER);
        assertThat(requestRecordMapper.records)
                .singleElement()
                .satisfies(record ->
                        assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.FAILED));
    }

    @Test
    void recordsProviderFailureAndRethrowsIt() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage("hello");
        RuntimeException failure = new BaseException(
                ErrorCodes.LLM_PROVIDER_ERROR,
                "Provider unavailable",
                HttpStatus.BAD_GATEWAY
        );
        geminiProvider.failure = failure;

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
                .isSameAs(failure);

        assertThat(requestRecordMapper.records)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.FAILED);
                    assertThat(record.getErrorCode()).isEqualTo(ErrorCodes.LLM_PROVIDER_ERROR);
                    assertThat(record.getErrorMessage()).isEqualTo("Provider unavailable");
                });
        assertThat(conversationManager.appendedRoles).containsExactly(ConversationRole.USER);
        assertThat(conversationManager.appendedContents).containsExactly("hello");
    }

    @Test
    void rejectsBlankUserMessageBeforeCreatingConversation() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("test-model");
        request.setUserMessage(" ");

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
                .isInstanceOf(BaseException.class)
                .hasMessage("User message must not be blank");

        assertThat(conversationManager.resolveCalls).isZero();
    }

    @Test
    void rejectsModelThatIsNotInAllowlist() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("gemini");
        request.setModel("unknown-model");

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
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

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
                .isInstanceOf(BaseException.class)
                .hasMessage("Model must not be blank");
    }

    @Test
    void rejectsUnknownProvider() {
        AppChatRequest request = new AppChatRequest();
        request.setProvider("unknown");
        request.setModel("test-model");

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
                .isInstanceOf(BaseException.class)
                .hasMessage("Unsupported provider: unknown");
    }

    @Test
    void rejectsBlankProvider() {
        AppChatRequest request = new AppChatRequest();
        request.setModel("test-model");

        assertThatThrownBy(() -> chatService.executeChat(command(request)))
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

    private ChatCommand command(AppChatRequest request) {
        return new ChatCommand(REQUEST_ID, USER_ID, request);
    }

    private LlmResponse streamResponse(
            String content,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        LlmResponse response = new LlmResponse();
        response.setContent(content);
        response.setProviderName(LlmProviderEnum.GEMINI.getCode());
        response.setPromptTokens(promptTokens);
        response.setCompletionTokens(completionTokens);
        response.setTotalTokens(totalTokens);
        return response;
    }

    private static final class CapturingProvider implements LlmProvider {

        private final LlmProviderEnum providerCode;
        private String lastRequestId;
        private LlmRequest lastRequest;
        private RuntimeException failure;
        private List<LlmResponse> streamResponses = List.of();

        private CapturingProvider(LlmProviderEnum providerCode) {
            this.providerCode = providerCode;
        }

        @Override
        public LlmProviderEnum getProviderCode() {
            return providerCode;
        }

        @Override
        public LlmResponse chat(String requestId, LlmRequest request) {
            lastRequestId = requestId;
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            LlmResponse response = new LlmResponse();
            response.setContent("ok");
            response.setProviderName(providerCode.getCode());
            return response;
        }

        @Override
        public Flux<LlmResponse> streamChat(String requestId, LlmRequest request) {
            lastRequestId = requestId;
            lastRequest = request;
            if (failure != null) {
                return Flux.error(failure);
            }
            return Flux.fromIterable(streamResponses);
        }
    }

    private static final class CapturingConversationManager extends ConversationManager {

        private final ChatConversation conversation;
        private final List<ConversationRole> appendedRoles = new ArrayList<>();
        private final List<String> appendedContents = new ArrayList<>();
        private final List<Message> history = new ArrayList<>();
        private int resolveCalls;

        private CapturingConversationManager(ChatConversation conversation) {
            super(null, null, null, null);
            this.conversation = conversation;
        }

        @Override
        public ChatConversation resolveConversation(Long userId, AppChatRequest request) {
            resolveCalls++;
            return conversation;
        }

        @Override
        public void appendMessage(
                ChatConversation conversation,
                ConversationRole role,
                String content
        ) {
            appendedRoles.add(role);
            appendedContents.add(content);
            history.add(new Message(role.name().toLowerCase(), content));
        }

        @Override
        public List<Message> loadMessageHistory(ChatConversation conversation) {
            return List.copyOf(history);
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
        public UsageStatistics getStatistics(Long userId) {
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
