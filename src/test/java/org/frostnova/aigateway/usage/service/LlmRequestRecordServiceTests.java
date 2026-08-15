package org.frostnova.aigateway.usage.service;

import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.model.UserRole;
import org.frostnova.aigateway.domain.model.LlmResponse;
import org.frostnova.aigateway.usage.mapper.LlmRequestRecordMapper;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestRecordContext;
import org.frostnova.aigateway.usage.model.LlmRequestRecordPage;
import org.frostnova.aigateway.usage.model.LlmRequestRecordQuery;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.frostnova.aigateway.usage.model.UsageStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LlmRequestRecordServiceTests {

    private static final long USER_ID = 42L;

    private CapturingRequestRecordMapper mapper;
    private LlmRequestRecordService service;

    @BeforeEach
    void setUp() {
        mapper = new CapturingRequestRecordMapper();
        service = new LlmRequestRecordService(mapper);
    }

    @Test
    void recordsSuccessfulTokenUsage() {
        LlmResponse response = new LlmResponse();
        response.setPromptTokens(12);
        response.setCompletionTokens(8);
        response.setTotalTokens(20);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 2, 12, 0);

        service.recordSuccess(
                LlmRequestRecordContext.builder()
                        .userId(USER_ID)
                        .requestId("request-success")
                        .provider("gemini")
                        .model("gemini-flash-latest")
                        .durationNanos(TimeUnit.MILLISECONDS.toNanos(350))
                        .requestedAt(requestedAt)
                        .build(),
                response
        );

        LlmRequestRecord record = capturedRecord();
        assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.SUCCESS);
        assertThat(record.getUserId()).isEqualTo(USER_ID);
        assertThat(record.getPromptTokens()).isEqualTo(12);
        assertThat(record.getCompletionTokens()).isEqualTo(8);
        assertThat(record.getTotalTokens()).isEqualTo(20);
        assertThat(record.getLatencyMs()).isEqualTo(350);
        assertThat(record.getRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void recordsProviderStatusAndTruncatesLongErrors() {
        String errorMessage = "x".repeat(600);
        RuntimeException exception = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                errorMessage,
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        service.recordFailure(
                LlmRequestRecordContext.builder()
                        .userId(USER_ID)
                        .requestId("request-failed")
                        .provider("groq")
                        .model("llama-3.3-70b-versatile")
                        .durationNanos(TimeUnit.MILLISECONDS.toNanos(125))
                        .requestedAt(LocalDateTime.of(2026, 8, 2, 12, 0))
                        .build(),
                exception
        );

        LlmRequestRecord record = capturedRecord();
        assertThat(record.getResultStatus()).isEqualTo(LlmRequestStatus.FAILED);
        assertThat(record.getUpstreamStatusCode()).isEqualTo(429);
        assertThat(record.getErrorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(record.getErrorMessage()).hasSize(512);
        assertThat(record.getPromptTokens()).isNull();
    }

    @Test
    void doesNotFailChatWhenRecordCannotBePersisted() {
        mapper.failOnInsert = true;

        assertThatCode(() -> service.recordSuccess(
                LlmRequestRecordContext.builder()
                        .userId(USER_ID)
                        .requestId("request-success")
                        .provider("gemini")
                        .model("gemini-flash-latest")
                        .durationNanos(TimeUnit.MILLISECONDS.toNanos(50))
                        .requestedAt(LocalDateTime.of(2026, 8, 2, 12, 0))
                        .build(),
                new LlmResponse()
        )).doesNotThrowAnyException();
    }

    @Test
    void queriesFilteredRequestRecordPage() {
        LlmRequestRecord item = LlmRequestRecord.builder()
                .requestId("request-page")
                .provider("gemini")
                .model("gemini-3.6-flash")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .latencyMs(120L)
                .requestedAt(LocalDateTime.of(2026, 8, 2, 12, 0))
                .build();
        mapper.pageRecords = List.of(item);
        mapper.totalCount = 41;

        LlmRequestRecordPage result = service.getRequestRecords(
                principal(UserRole.USER),
                " request-page ",
                "gemini",
                "gemini-3.6-flash",
                LlmRequestStatus.SUCCESS,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 3, 0, 0),
                3,
                20
        );

        assertThat(result.items()).containsExactly(item);
        assertThat(result.totalItems()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(mapper.lastQuery.requestId()).isEqualTo("request-page");
        assertThat(mapper.lastQuery.userId()).isEqualTo(USER_ID);
        assertThat(mapper.lastQuery.offset()).isEqualTo(40);
        assertThat(mapper.lastQuery.limit()).isEqualTo(20);
    }

    @Test
    void scopesStatisticsToUsersButAllowsAdministratorsToViewAll() {
        mapper.statistics = new UsageStatistics();

        service.getStatistics(principal(UserRole.USER));
        assertThat(mapper.lastStatisticsUserId).isEqualTo(USER_ID);

        service.getStatistics(principal(UserRole.ADMIN));
        assertThat(mapper.lastStatisticsUserId).isNull();
    }

    private AuthPrincipal principal(UserRole role) {
        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserId(USER_ID);
        principal.setRole(role);
        return principal;
    }

    private LlmRequestRecord capturedRecord() {
        assertThat(mapper.records).hasSize(1);
        return mapper.records.getFirst();
    }

    private static final class CapturingRequestRecordMapper implements LlmRequestRecordMapper {

        private final List<LlmRequestRecord> records = new ArrayList<>();
        private boolean failOnInsert;
        private List<LlmRequestRecord> pageRecords = List.of();
        private long totalCount;
        private LlmRequestRecordQuery lastQuery;
        private UsageStatistics statistics;
        private Long lastStatisticsUserId;

        @Override
        public int insert(LlmRequestRecord record) {
            if (failOnInsert) {
                throw new IllegalStateException("database unavailable");
            }
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
            lastQuery = query;
            return pageRecords;
        }

        @Override
        public long count(LlmRequestRecordQuery query) {
            lastQuery = query;
            return totalCount;
        }

        @Override
        public UsageStatistics getStatistics(Long userId) {
            lastStatisticsUserId = userId;
            return statistics;
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
