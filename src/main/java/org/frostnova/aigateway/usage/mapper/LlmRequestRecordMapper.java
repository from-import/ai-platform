package org.frostnova.aigateway.usage.mapper;

import org.apache.ibatis.annotations.Param;
import org.frostnova.aigateway.usage.model.LlmRequestRecord;
import org.frostnova.aigateway.usage.model.LlmRequestRecordQuery;
import org.frostnova.aigateway.usage.model.UsageStatistics;

import java.util.List;

public interface LlmRequestRecordMapper {

    int insert(LlmRequestRecord record);

    LlmRequestRecord findById(@Param("id") Long id);

    LlmRequestRecord findByRequestId(@Param("requestId") String requestId);

    List<LlmRequestRecord> findAll();

    List<LlmRequestRecord> findPage(LlmRequestRecordQuery query);

    long count(LlmRequestRecordQuery query);

    UsageStatistics getStatistics(@Param("userId") Long userId);

    int update(LlmRequestRecord record);

    int deleteById(@Param("id") Long id);
}
