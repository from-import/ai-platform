package org.frostnova.aigateway.usage.controller;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.usage.model.LlmRequestRecordPage;
import org.frostnova.aigateway.usage.model.LlmRequestStatus;
import org.frostnova.aigateway.usage.model.UsageStatistics;
import org.frostnova.aigateway.usage.service.LlmRequestRecordService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final LlmRequestRecordService requestRecordService;

    public UsageController(LlmRequestRecordService requestRecordService) {
        this.requestRecordService = requestRecordService;
    }

    @GetMapping("/statistics")
    public UsageStatistics getStatistics() {
        return requestRecordService.getStatistics();
    }

    @GetMapping("/requests")
    public LlmRequestRecordPage getRequestRecords(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime requestedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime requestedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return requestRecordService.getRequestRecords(
                requestId,
                provider,
                model,
                parseStatus(status),
                requestedFrom,
                requestedTo,
                page,
                pageSize
        );
    }

    private LlmRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return LlmRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "status must be SUCCESS or FAILED");
        }
    }
}
