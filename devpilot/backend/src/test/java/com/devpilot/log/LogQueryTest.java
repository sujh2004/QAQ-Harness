package com.devpilot.log;

import com.devpilot.common.api.PageResponse;
import com.devpilot.log.model.ErrorSummaryResponse;
import com.devpilot.log.model.ImportLogsRequest;
import com.devpilot.log.model.LogEntryRequest;
import com.devpilot.log.model.LogEntryResponse;
import com.devpilot.log.service.LogService;
import com.devpilot.project.model.CreateProjectRequest;
import com.devpilot.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: log queries are scoped to one project, support every documented filter and are bounded.
 */
@SpringBootTest
@ActiveProfiles("test")
class LogQueryTest {

    private static final LocalDateTime INCIDENT = LocalDateTime.of(2026, 8, 16, 10, 31, 2);

    @Autowired
    private LogService logService;

    @Autowired
    private ProjectService projectService;

    private long projectId;
    private long otherProjectId;

    @BeforeEach
    void seedLogs() {
        projectId = newProject();
        otherProjectId = newProject();

        logService.importLogs(projectId, new ImportLogsRequest(List.of(
                entry("order-service", "ERROR", "t-1001", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException", INCIDENT),
                entry("order-service", "ERROR", "t-1002", "com.demo.order.OrderService",
                        "Cannot invoke \"CouponInfo.getDiscountAmount()\" because \"coupon\" is null",
                        "java.lang.NullPointerException", INCIDENT.plusMinutes(3)),
                entry("order-service", "WARN", "t-1001", "com.demo.order.CouponClient",
                        "coupon service responded 503", null, INCIDENT.minusSeconds(1)),
                entry("order-service", "ERROR", "t-2001", "com.demo.order.OrderMapper",
                        "query listOrdersByUser timed out", "java.sql.SQLTimeoutException",
                        INCIDENT.plusDays(1)),
                entry("inventory-service", "INFO", "t-2001", "com.demo.inventory.InventoryService",
                        "stock reserved", null, INCIDENT.plusDays(1)))));

        logService.importLogs(otherProjectId, new ImportLogsRequest(List.of(
                entry("order-service", "ERROR", "t-1001", "other.project.Service",
                        "should never leak across projects", "java.lang.IllegalStateException", INCIDENT))));
    }

    @Test
    void isolatesLogsByProject() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, null, null, null, null, null, 0, 50);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.items())
                .extracting(LogEntryResponse::message)
                .doesNotContain("should never leak across projects");
    }

    @Test
    void filtersByServiceName() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, "inventory-service", null, null, null, null, null, 0, 50);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().serviceName()).isEqualTo("inventory-service");
    }

    @Test
    void filtersByLevelCaseInsensitively() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, "error", null, null, null, null, 0, 50);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).allSatisfy(entry -> assertThat(entry.level()).isEqualTo("ERROR"));
    }

    @Test
    void filtersByKeywordAcrossMessageAndExceptionType() {
        assertThat(logService.search(projectId, null, null, "getDiscountAmount", null, null, null, 0, 50)
                .total()).isEqualTo(2);
        assertThat(logService.search(projectId, null, null, "SQLTimeoutException", null, null, null, 0, 50)
                .total()).isEqualTo(1);
    }

    @Test
    void filtersByTraceId() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, null, null, "t-1001", null, null, 0, 50);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).allSatisfy(entry -> assertThat(entry.traceId()).isEqualTo("t-1001"));
    }

    @Test
    void filtersByTimeRange() {
        PageResponse<LogEntryResponse> page = logService.search(
                projectId, null, null, null, null, INCIDENT.minusMinutes(1), INCIDENT.plusMinutes(1), 0, 50);

        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void returnsNewestLinesFirst() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, null, null, null, null, null, 0, 50);

        assertThat(page.items().getFirst().logTime()).isEqualTo(INCIDENT.plusDays(1));
    }

    @Test
    void capsThePageSizeAtTheDocumentedLimit() {
        logService.importLogs(projectId, new ImportLogsRequest(IntStream.range(0, 120)
                .mapToObj(index -> entry("bulk-service", "INFO", "t-bulk", "bulk",
                        "line " + index, null, INCIDENT.plusSeconds(index)))
                .toList()));

        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, null, null, null, null, null, 0, 5_000);

        assertThat(page.size()).isEqualTo(LogService.MAX_PAGE_SIZE);
        assertThat(page.items()).hasSize(LogService.MAX_PAGE_SIZE);
        assertThat(page.total()).isEqualTo(125);
    }

    @Test
    void groupsRecentErrorsSoTheAgentDoesNotHaveToReadEveryLine() {
        // The seeded incident is in the past, so the window has to reach back to it.
        int hours = (int) java.time.Duration.between(INCIDENT, LocalDateTime.now()).toHours() + 48;

        List<ErrorSummaryResponse> summary = logService.summarizeErrors(projectId, hours);

        assertThat(summary).isNotEmpty();
        ErrorSummaryResponse npe = summary.stream()
                .filter(group -> "java.lang.NullPointerException".equals(group.exceptionType()))
                .findFirst()
                .orElseThrow();
        assertThat(npe.serviceName()).isEqualTo("order-service");
        assertThat(npe.occurrences()).isEqualTo(2);
        assertThat(npe.firstSeen()).isEqualTo(INCIDENT);
        assertThat(npe.lastSeen()).isEqualTo(INCIDENT.plusMinutes(3));
        assertThat(npe.sampleMessage()).contains("getDiscountAmount");
        assertThat(summary).noneSatisfy(group ->
                assertThat(group.exceptionType()).isEqualTo("java.lang.IllegalStateException"));
    }

    @Test
    void returnsNothingWhenNoLineMatches() {
        PageResponse<LogEntryResponse> page =
                logService.search(projectId, null, null, "no-such-keyword", null, null, null, 0, 50);

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    private long newProject() {
        return projectService.create(new CreateProjectRequest(
                        "log-test", "log-" + UUID.randomUUID().toString().substring(0, 8),
                        null, "/srv/repos/log-test", null))
                .id();
    }

    private static LogEntryRequest entry(
            String serviceName,
            String level,
            String traceId,
            String logger,
            String message,
            String exceptionType,
            LocalDateTime logTime) {
        return new LogEntryRequest(
                serviceName, level, traceId, logger, message, exceptionType, null, logTime);
    }
}
