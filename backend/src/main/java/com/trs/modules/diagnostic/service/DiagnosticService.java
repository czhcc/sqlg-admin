package com.trs.modules.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.mapper.TaskMapper;
import com.trs.modules.task.service.TaskService;
import com.trs.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断工具服务,串联"列出检查项 → 创建诊断任务 → 拉取报告 → 列出最近报告"流程。
 * <p>
 * 复用 {@link TaskService} 创建 DIAG_CUSTOM 异步任务,并通过 {@link TaskMapper} 查询历史报告。
 *
 * @author czh
 * @date 2026/0728
 */
@Service
public class DiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DiagnosticCheckRegistry checkRegistry;
    private final ConnectionVisibilityHelper connectionVisibilityHelper;
    private final TaskService taskService;
    private final TaskMapper taskMapper;

    public DiagnosticService(DiagnosticCheckRegistry checkRegistry,
                              ConnectionVisibilityHelper connectionVisibilityHelper,
                              TaskService taskService,
                              TaskMapper taskMapper) {
        this.checkRegistry = checkRegistry;
        this.connectionVisibilityHelper = connectionVisibilityHelper;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
    }

    public Map<String, Object> listConnections() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", connectionVisibilityHelper.listConnectionDtosForCurrentUser());
        return result;
    }

    public List<Map<String, Object>> listChecks() {
        return checkRegistry.toMetadata();
    }

    public Task runChecks(Long connectionId, List<String> checkIds, String name, User user) {
        if (connectionId == null) throw new IllegalArgumentException("必须选择一个连接");
        if (checkIds == null || checkIds.isEmpty()) throw new IllegalArgumentException("至少选择一项诊断检查");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("checkIds", checkIds);
        String displayName = (name == null || name.isBlank())
                ? "诊断任务: " + checkIds.size() + " 项检查" : name;
        return taskService.create("DIAG_CUSTOM", connectionId, null, null, params, user);
    }

    public Map<String, Object> getReport(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        if (!"DIAG_CUSTOM".equals(task.getTaskType())) {
            throw new IllegalArgumentException("任务 " + taskId + " 不是诊断任务");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> taskInfo = new LinkedHashMap<>();
        taskInfo.put("id", task.getId());
        taskInfo.put("taskNo", task.getTaskNo());
        taskInfo.put("name", task.getName());
        taskInfo.put("status", task.getStatus());
        taskInfo.put("connectionId", task.getConnectionId());
        taskInfo.put("connectionName", task.getConnectionName());
        taskInfo.put("progressPercent", task.getProgressPercent());
        taskInfo.put("currentStage", task.getCurrentStage());
        taskInfo.put("startTime", task.getStartTime());
        taskInfo.put("endTime", task.getEndTime());
        taskInfo.put("successCount", task.getSuccessCount());
        taskInfo.put("failedCount", task.getFailedCount());
        taskInfo.put("username", task.getUsername());
        result.put("task", taskInfo);

        Map<String, Object> report = parseReport(task.getResultSummary());
        result.put("report", report);
        result.put("errorMessage", task.getErrorMessage());
        return result;
    }

    public Map<String, Object> listRecentReports(Long connectionId, int page, int size) {
        if (size > 100) size = 100;
        int offset = (page - 1) * size;
        List<Task> rows = taskMapper.selectPage(offset, size, null,
                "DIAGNOSTIC", "DIAG_CUSTOM", connectionId, null, null, null, null, null, null);
        long total = taskMapper.selectCount(null, "DIAGNOSTIC", "DIAG_CUSTOM", connectionId,
                null, null, null, null, null, null);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Task t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("name", t.getName());
            m.put("status", t.getStatus());
            m.put("connectionId", t.getConnectionId());
            m.put("connectionName", t.getConnectionName());
            m.put("startTime", t.getStartTime());
            m.put("endTime", t.getEndTime());
            m.put("progressPercent", t.getProgressPercent());
            m.put("currentStage", t.getCurrentStage());
            m.put("successCount", t.getSuccessCount());
            m.put("failedCount", t.getFailedCount());
            m.put("issueCount", extractIssueCount(t.getResultSummary()));
            m.put("criticalCount", extractSeverityCount(t.getResultSummary(), "CRITICAL"));
            m.put("errorCount", extractSeverityCount(t.getResultSummary(), "ERROR"));
            m.put("warningCount", extractSeverityCount(t.getResultSummary(), "WARNING"));
            m.put("username", t.getUsername());
            items.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", items);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private Map<String, Object> parseReport(String resultSummary) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (resultSummary == null || resultSummary.isBlank()) {
            report.put("available", false);
            return report;
        }
        try {
            JsonNode root = MAPPER.readTree(resultSummary);
            report.put("available", true);
            report.put("summary", jsonToMap(root.get("summary")));
            report.put("issues", jsonToList(root.get("issues")));
            report.put("perCheckCounts", jsonToMap(root.get("perCheckCounts")));
        } catch (Exception e) {
            log.warn("Failed to parse diagnostic report: {}", e.getMessage());
            report.put("available", false);
            report.put("raw", resultSummary);
        }
        return report;
    }

    private int extractIssueCount(String resultSummary) {
        if (resultSummary == null) return 0;
        try {
            JsonNode root = MAPPER.readTree(resultSummary);
            JsonNode summary = root.get("summary");
            if (summary != null && summary.has("totalIssues")) return summary.get("totalIssues").asInt();
        } catch (Exception ignored) {}
        return 0;
    }

    private int extractSeverityCount(String resultSummary, String severity) {
        if (resultSummary == null) return 0;
        try {
            JsonNode root = MAPPER.readTree(resultSummary);
            JsonNode bySev = root.path("summary").path("bySeverity").path(severity);
            if (!bySev.isMissingNode()) return bySev.asInt();
        } catch (Exception ignored) {}
        return 0;
    }

    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || node.isNull()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isObject()) m.put(e.getKey(), jsonToMap(v));
            else if (v.isNumber()) m.put(e.getKey(), v.numberValue());
            else if (v.isBoolean()) m.put(e.getKey(), v.booleanValue());
            else m.put(e.getKey(), v.asText());
        });
        return m;
    }

    private List<Object> jsonToList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        List<Object> list = new ArrayList<>();
        for (JsonNode elem : node) {
            if (elem.isObject()) list.add(jsonToMap(elem));
            else if (elem.isNumber()) list.add(elem.numberValue());
            else if (elem.isBoolean()) list.add(elem.booleanValue());
            else list.add(elem.asText());
        }
        return list;
    }
}
