package org.frostnova.aigateway.usage.mapper;

import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.service.AuthService;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestRecordQuery;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.frostnova.aigateway.usage.model.UsageStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class LlmRequestRecordMapperTests {

    @Autowired
    private LlmRequestRecordMapper mapper;

    @Autowired
    private AuthService authService;

    @Test
    void createsReadsUpdatesAndDeletesRequestRecord() {
        LlmRequestRecord record = LlmRequestRecord.builder()
                .requestId("request-001")
                .provider("gemini")
                .model("gemini-flash-latest")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .promptTokens(12)
                .completionTokens(8)
                .totalTokens(20)
                .latencyMs(350L)
                .requestedAt(LocalDateTime.of(2026, 8, 2, 10, 30))
                .build();

        assertThat(mapper.insert(record)).isEqualTo(1);
        assertThat(record.getId()).isNotNull();

        LlmRequestRecord stored = mapper.findById(record.getId());
        assertThat(stored.getRequestId()).isEqualTo("request-001");
        assertThat(stored.getResultStatus()).isEqualTo(LlmRequestStatus.SUCCESS);
        assertThat(stored.getTotalTokens()).isEqualTo(20);
        assertThat(mapper.findByRequestId("request-001").getId()).isEqualTo(record.getId());
        assertThat(mapper.findAll()).extracting(LlmRequestRecord::getId)
                .contains(record.getId());

        record.setResultStatus(LlmRequestStatus.FAILED);
        record.setPromptTokens(null);
        record.setCompletionTokens(null);
        record.setTotalTokens(null);
        record.setUpstreamStatusCode(429);
        record.setErrorCode("PROVIDER_ERROR");
        record.setErrorMessage("Rate limit exceeded");

        assertThat(mapper.update(record)).isEqualTo(1);

        LlmRequestRecord updated = mapper.findById(record.getId());
        assertThat(updated.getResultStatus()).isEqualTo(LlmRequestStatus.FAILED);
        assertThat(updated.getPromptTokens()).isNull();
        assertThat(updated.getUpstreamStatusCode()).isEqualTo(429);
        assertThat(updated.getErrorCode()).isEqualTo("PROVIDER_ERROR");

        assertThat(mapper.deleteById(record.getId())).isEqualTo(1);
        assertThat(mapper.findById(record.getId())).isNull();
    }

    @Test
    void aggregatesUsageStatistics() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 2, 10, 30);
        mapper.insert(LlmRequestRecord.builder()
                .requestId("statistics-success")
                .provider("gemini")
                .model("gemini-flash-latest")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .promptTokens(12)
                .completionTokens(8)
                .totalTokens(20)
                .latencyMs(300L)
                .requestedAt(requestedAt)
                .build());
        mapper.insert(LlmRequestRecord.builder()
                .requestId("statistics-failure")
                .provider("groq")
                .model("llama-3.3-70b-versatile")
                .resultStatus(LlmRequestStatus.FAILED)
                .latencyMs(100L)
                .errorCode("PROVIDER_ERROR")
                .requestedAt(requestedAt.plusSeconds(1))
                .build());

        UsageStatistics statistics = mapper.getStatistics(null);

        assertThat(statistics.getTotalRequests()).isEqualTo(2);
        assertThat(statistics.getSuccessfulRequests()).isEqualTo(1);
        assertThat(statistics.getFailedRequests()).isEqualTo(1);
        assertThat(statistics.getPromptTokens()).isEqualTo(12);
        assertThat(statistics.getCompletionTokens()).isEqualTo(8);
        assertThat(statistics.getTotalTokens()).isEqualTo(20);
        assertThat(statistics.getAverageLatencyMs()).isEqualTo(200.0);
    }

    @Test
    void filtersAndPaginatesRequestRecords() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 2, 10, 30);
        mapper.insert(LlmRequestRecord.builder()
                .requestId("page-gemini-success")
                .provider("gemini")
                .model("gemini-3.6-flash")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .latencyMs(100L)
                .requestedAt(requestedAt)
                .build());
        mapper.insert(LlmRequestRecord.builder()
                .requestId("page-gemini-failure")
                .provider("gemini")
                .model("gemini-3.6-flash")
                .resultStatus(LlmRequestStatus.FAILED)
                .latencyMs(200L)
                .requestedAt(requestedAt.plusMinutes(1))
                .build());
        mapper.insert(LlmRequestRecord.builder()
                .requestId("page-groq-success")
                .provider("groq")
                .model("llama-3.3-70b-versatile")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .latencyMs(300L)
                .requestedAt(requestedAt.plusMinutes(2))
                .build());

        LlmRequestRecordQuery query = new LlmRequestRecordQuery(
                null,
                null,
                "gemini",
                "gemini-3.6-flash",
                LlmRequestStatus.SUCCESS,
                requestedAt.minusMinutes(1),
                requestedAt.plusMinutes(2),
                0,
                10
        );
        List<LlmRequestRecord> records = mapper.findPage(query);

        assertThat(mapper.count(query)).isEqualTo(1);
        assertThat(records).extracting(LlmRequestRecord::getRequestId)
                .containsExactly("page-gemini-success");
    }

    @Test
    void isolatesRecordsAndStatisticsByUser() {
        UserView firstUser = authService.register(new RegisterRequest(
                "usage-user-one",
                "correct-password",
                "Usage User One"
        ));
        UserView secondUser = authService.register(new RegisterRequest(
                "usage-user-two",
                "correct-password",
                "Usage User Two"
        ));
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 2, 10, 30);

        mapper.insert(LlmRequestRecord.builder()
                .userId(firstUser.id())
                .requestId("user-one-request")
                .provider("gemini")
                .model("gemini-flash-latest")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .totalTokens(10)
                .latencyMs(100L)
                .requestedAt(requestedAt)
                .build());
        mapper.insert(LlmRequestRecord.builder()
                .userId(secondUser.id())
                .requestId("user-two-request")
                .provider("groq")
                .model("llama-3.3-70b-versatile")
                .resultStatus(LlmRequestStatus.SUCCESS)
                .totalTokens(20)
                .latencyMs(200L)
                .requestedAt(requestedAt.plusSeconds(1))
                .build());

        LlmRequestRecordQuery firstUserQuery = new LlmRequestRecordQuery(
                firstUser.id(),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        );

        assertThat(mapper.findPage(firstUserQuery))
                .extracting(LlmRequestRecord::getRequestId)
                .containsExactly("user-one-request");
        assertThat(mapper.getStatistics(firstUser.id()).getTotalRequests()).isEqualTo(1);
        assertThat(mapper.getStatistics(firstUser.id()).getTotalTokens()).isEqualTo(10);
        assertThat(mapper.getStatistics(null).getTotalRequests()).isEqualTo(2);
    }
}
