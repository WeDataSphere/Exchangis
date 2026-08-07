package com.webank.wedatasphere.exchangis.job.server.service.impl;

import com.webank.wedatasphere.exchangis.job.launcher.domain.task.TaskStatus;
import com.webank.wedatasphere.exchangis.job.log.LogQuery;
import com.webank.wedatasphere.exchangis.job.log.LogResult;
import com.webank.wedatasphere.exchangis.job.server.vo.ExchangisCategoryLogVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultJobExecuteService#resultToCategoryLog}
 * (resultToCategoryLog 空指针保护单元测试).
 *
 * <p>Verifies that a null {@link LogResult} (or a LogResult whose logs list is null) - which the
 * upstream log fetcher (RPC remote response / queryLogs) can return - no longer NPEs at
 * {@code logResult.getLogs().isEmpty()}, and instead yields an empty category log (logs: {}),
 * identical to the normal empty-logs case (so the frontend is unaffected).
 * (验证 LogResult 为 null 或其 logs 列表为 null--上游日志抓取器 RPC 远端响应/queryLogs 可能返回--
 * 不再在 logResult.getLogs().isEmpty() 空指针，而是产出空分类日志(logs:{})，与正常空日志场景一致，
 * 前端无感。)
 */
class DefaultJobExecuteServiceTest {

    private final DefaultJobExecuteService service = new DefaultJobExecuteService();

    @Test
    @DisplayName("null logs list does not NPE and yields empty logs (logs 列表为 null 不空指针、返回空日志)")
    void testResultToCategoryLogNullLogsList() {
        // Upstream log fetcher (RPC remote response / queryLogs) can return a LogResult whose
        // logs list is null; previously this NPEd at logResult.getLogs().isEmpty().
        // 上游日志抓取器（RPC 远端响应/queryLogs）可能返回 logs 为 null 的 LogResult；
        // 原先在 logResult.getLogs().isEmpty() 处空指针。
        LogResult nullLogsResult = new LogResult(0, false, null);
        ExchangisCategoryLogVo vo = service.resultToCategoryLog(
                new LogQuery(1, 100, null, null, null), nullLogsResult, TaskStatus.Inited);
        assertNotNull(vo, "categoryLogVo should not be null (categoryLogVo 不应为空)");
        assertNotNull(vo.getLogs(), "logs map should be present (logs map 应存在)");
        assertTrue(vo.getLogs().isEmpty(),
                "logs map should be empty (logs map 应为空)");
    }

    @Test
    @DisplayName("null logResult does not NPE and yields empty logs (logResult 为 null 不空指针、返回空日志)")
    void testResultToCategoryLogNullLogResult() {
        ExchangisCategoryLogVo vo = service.resultToCategoryLog(
                new LogQuery(1, 100, null, null, null), null, TaskStatus.Success);
        assertNotNull(vo, "categoryLogVo should not be null (categoryLogVo 不应为空)");
        assertNotNull(vo.getLogs(), "logs map should be present (logs map 应存在)");
        assertTrue(vo.getLogs().isEmpty(),
                "logs map should be empty (logs map 应为空)");
    }
}
