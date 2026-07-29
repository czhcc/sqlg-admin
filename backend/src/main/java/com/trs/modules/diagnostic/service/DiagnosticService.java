package com.trs.modules.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.mapper.TaskMapper;
import com.trs.modules.task.service.TaskService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
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
    private final SqlgGraphRegistry registry;

    public DiagnosticService(DiagnosticCheckRegistry checkRegistry,
                              ConnectionVisibilityHelper connectionVisibilityHelper,
                              TaskService taskService,
                              TaskMapper taskMapper,
                              SqlgGraphRegistry registry) {
        this.checkRegistry = checkRegistry;
        this.connectionVisibilityHelper = connectionVisibilityHelper;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.registry = registry;
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

    /**
     * 同步执行单项轻量检查,直接返回 issues(不创建异步任务)。
     * 仅允许非 heavy 检查走此路径,heavy 检查必须通过 runChecks 异步执行。
     */
    public Map<String, Object> runSingleCheck(Long connectionId, String checkId) {
        DiagnosticCheck check = checkRegistry.get(checkId);
        if (check == null) throw new IllegalArgumentException("检查项不存在: " + checkId);
        if (check.isHeavy()) throw new IllegalArgumentException("耗时检查项(" + checkId + ")必须通过异步任务执行");

        var graph = registry.get(connectionId);
        try { graph.tx().rollback(); } catch (Exception ignored) {}

        List<DiagnosticIssue> issues;
        try {
            issues = check.execute(graph, new com.trs.modules.task.engine.TaskContext(
                    null, "SYNC-" + checkId, connectionId, "{}", "/tmp"), null);
            graph.tx().rollback();
        } catch (Exception e) {
            throw new IllegalStateException("检查执行失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> checkMeta = new LinkedHashMap<>();
        checkMeta.put("id", check.id());
        checkMeta.put("category", check.category());
        checkMeta.put("displayName", check.displayName());
        result.put("check", checkMeta);
        result.put("issueCount", issues.size());

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        bySeverity.put(DiagnosticIssue.INFO, 0L);
        bySeverity.put(DiagnosticIssue.WARNING, 0L);
        bySeverity.put(DiagnosticIssue.ERROR, 0L);
        bySeverity.put(DiagnosticIssue.CRITICAL, 0L);
        List<Map<String, Object>> issueList = new ArrayList<>();
        for (DiagnosticIssue issue : issues) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", issue.level());
            m.put("category", issue.category());
            m.put("code", issue.code());
            m.put("target", issue.target());
            m.put("message", issue.message());
            m.put("suggestion", issue.suggestion());
            m.put("jumpTarget", issue.jumpTarget());
            issueList.add(m);
            bySeverity.merge(issue.level(), 1L, Long::sum);
        }
        result.put("issues", issueList);
        result.put("bySeverity", bySeverity);
        return result;
    }

    /**
     * 导出诊断报告为 JSON 字符串(供前端下载)。
     */
    public String exportReportAsJson(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        if (!"DIAG_CUSTOM".equals(task.getTaskType())) {
            throw new IllegalArgumentException("任务 " + taskId + " 不是诊断任务");
        }
        if (task.getResultSummary() == null || task.getResultSummary().isBlank()) {
            throw new IllegalArgumentException("报告尚未生成(任务可能还在执行中)");
        }
        return task.getResultSummary();
    }

    public String getReportFileName(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) return "diagnostic_report.json";
        return "diagnostic_report_" + task.getTaskNo() + ".json";
    }

    /**
     * 导出诊断报告为 Excel(.xlsx),含两个 sheet:Summary(汇总)和 Issues(问题明细,严重级别着色)。
     */
    public byte[] exportReportAsExcel(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        if (!"DIAG_CUSTOM".equals(task.getTaskType())) {
            throw new IllegalArgumentException("任务 " + taskId + " 不是诊断任务");
        }
        Map<String, Object> reportData = parseReport(task.getResultSummary());
        Map<String, Object> summary = (Map<String, Object>) reportData.get("summary");
        List<Object> issues = (List<Object>) reportData.get("issues");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet summarySheet = wb.createSheet("Summary");
            org.apache.poi.ss.usermodel.Sheet issuesSheet = wb.createSheet("Issues");

            var headerStyle = wb.createCellStyle();
            var headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            int r = 0;
            var row = summarySheet.createRow(r++);
            setCell(row, 0, "字段", headerStyle);
            setCell(row, 1, "值", headerStyle);
            writeSummaryRow(summarySheet, r++, "任务编号", task.getTaskNo());
            writeSummaryRow(summarySheet, r++, "任务名称", task.getName());
            writeSummaryRow(summarySheet, r++, "连接", task.getConnectionName());
            writeSummaryRow(summarySheet, r++, "创建人", task.getUsername());
            writeSummaryRow(summarySheet, r++, "开始时间", String.valueOf(task.getStartTime()));
            writeSummaryRow(summarySheet, r++, "结束时间", String.valueOf(task.getEndTime()));
            writeSummaryRow(summarySheet, r++, "检查项总数", summary == null ? "0" : String.valueOf(summary.get("totalChecks")));
            writeSummaryRow(summarySheet, r++, "成功项数", summary == null ? "0" : String.valueOf(summary.get("successChecks")));
            writeSummaryRow(summarySheet, r++, "失败项数", summary == null ? "0" : String.valueOf(summary.get("failedChecks")));
            writeSummaryRow(summarySheet, r++, "问题总数", summary == null ? "0" : String.valueOf(summary.get("totalIssues")));
            Map<String, Object> bySeverity = summary == null ? null : (Map<String, Object>) summary.get("bySeverity");
            if (bySeverity != null) {
                for (String sev : new String[]{"CRITICAL", "ERROR", "WARNING", "INFO"}) {
                    writeSummaryRow(summarySheet, r++, "  " + sev, String.valueOf(bySeverity.getOrDefault(sev, 0)));
                }
            }
            Map<String, Object> byCategory = summary == null ? null : (Map<String, Object>) summary.get("byCategory");
            if (byCategory != null) {
                writeSummaryRow(summarySheet, r++, "按类别分布", "");
                for (var entry : byCategory.entrySet()) {
                    writeSummaryRow(summarySheet, r++, "  " + entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            // Issues sheet with severity color
            var issueHeaderRow = issuesSheet.createRow(0);
            String[] cols = {"级别", "类别", "编码", "定位", "描述", "建议", "跳转目标"};
            for (int c = 0; c < cols.length; c++) setCell(issueHeaderRow, c, cols[c], headerStyle);

            var criticalStyle = colorStyle(wb, org.apache.poi.ss.usermodel.IndexedColors.RED);
            var errorStyle = colorStyle(wb, org.apache.poi.ss.usermodel.IndexedColors.ORANGE);
            var warnStyle = colorStyle(wb, org.apache.poi.ss.usermodel.IndexedColors.YELLOW);
            var infoStyle = colorStyle(wb, org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE);

            int issueRowIdx = 1;
            if (issues != null) {
                for (Object obj : issues) {
                    Map<String, Object> issue = (Map<String, Object>) obj;
                    String level = String.valueOf(issue.getOrDefault("level", ""));
                    var dataRow = issuesSheet.createRow(issueRowIdx++);
                    var style = switch (level) {
                        case "CRITICAL" -> criticalStyle;
                        case "ERROR" -> errorStyle;
                        case "WARNING" -> warnStyle;
                        case "INFO" -> infoStyle;
                        default -> null;
                    };
                    setCellStyled(dataRow, 0, level, style);
                    setCellStyled(dataRow, 1, String.valueOf(issue.getOrDefault("category", "")), style);
                    setCellStyled(dataRow, 2, String.valueOf(issue.getOrDefault("code", "")), style);
                    setCellStyled(dataRow, 3, String.valueOf(issue.getOrDefault("target", "")), style);
                    setCellStyled(dataRow, 4, String.valueOf(issue.getOrDefault("message", "")), style);
                    setCellStyled(dataRow, 5, String.valueOf(issue.getOrDefault("suggestion", "")), style);
                    setCellStyled(dataRow, 6, String.valueOf(issue.getOrDefault("jumpTarget", "")), style);
                }
            }
            for (int c = 0; c < cols.length; c++) issuesSheet.autoSizeColumn(c);

            var baos = new java.io.ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("生成 Excel 失败: " + e.getMessage(), e);
        }
    }

    private void writeSummaryRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIdx, String key, String val) {
        var row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(val == null ? "" : val);
    }

    private void setCell(org.apache.poi.ss.usermodel.Row row, int col, String val, org.apache.poi.ss.usermodel.CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(val == null ? "" : val);
        if (style != null) cell.setCellStyle(style);
    }

    private void setCellStyled(org.apache.poi.ss.usermodel.Row row, int col, String val, org.apache.poi.ss.usermodel.CellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(val == null ? "" : val);
        if (style != null) cell.setCellStyle(style);
    }

    private org.apache.poi.ss.usermodel.CellStyle colorStyle(org.apache.poi.xssf.usermodel.XSSFWorkbook wb,
                                                                org.apache.poi.ss.usermodel.IndexedColors color) {
        var style = wb.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    public String getReportExcelFileName(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) return "diagnostic_report.xlsx";
        return "diagnostic_report_" + task.getTaskNo() + ".xlsx";
    }

    /**
     * 对比两份报告,按 (target+code) 作为问题唯一键,分类为新增/已修复/持续存在。
     */
    public Map<String, Object> compareReports(Long baselineTaskId, Long currentTaskId) {
        Task baselineTask = taskMapper.selectById(baselineTaskId);
        Task currentTask = taskMapper.selectById(currentTaskId);
        if (baselineTask == null || currentTask == null) {
            throw new IllegalArgumentException("baselineTaskId 或 currentTaskId 对应的任务不存在");
        }
        if (!"DIAG_CUSTOM".equals(baselineTask.getTaskType()) || !"DIAG_CUSTOM".equals(currentTask.getTaskType())) {
            throw new IllegalArgumentException("两个任务都必须是诊断任务");
        }

        Map<String, Map<String, Object>> baselineIssues = indexIssues(baselineTask.getResultSummary());
        Map<String, Map<String, Object>> currentIssues = indexIssues(currentTask.getResultSummary());

        List<Map<String, Object>> newIssues = new ArrayList<>();
        List<Map<String, Object>> fixedIssues = new ArrayList<>();
        List<Map<String, Object>> persistentIssues = new ArrayList<>();

        for (var entry : currentIssues.entrySet()) {
            if (baselineIssues.containsKey(entry.getKey())) {
                entry.getValue().put("baselineLevel", baselineIssues.get(entry.getKey()).get("level"));
                persistentIssues.add(entry.getValue());
            } else {
                newIssues.add(entry.getValue());
            }
        }
        for (var entry : baselineIssues.entrySet()) {
            if (!currentIssues.containsKey(entry.getKey())) {
                fixedIssues.add(entry.getValue());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("baseline", buildTaskBrief(baselineTask));
        tasks.put("current", buildTaskBrief(currentTask));
        result.put("tasks", tasks);
        result.put("newIssues", newIssues);
        result.put("fixedIssues", fixedIssues);
        result.put("persistentIssues", persistentIssues);
        result.put("newCount", newIssues.size());
        result.put("fixedCount", fixedIssues.size());
        result.put("persistentCount", persistentIssues.size());
        return result;
    }

    private Map<String, Map<String, Object>> indexIssues(String resultSummary) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        if (resultSummary == null || resultSummary.isBlank()) return indexed;
        try {
            JsonNode root = MAPPER.readTree(resultSummary);
            JsonNode issuesNode = root.get("issues");
            if (issuesNode == null || !issuesNode.isArray()) return indexed;
            for (JsonNode elem : issuesNode) {
                String target = elem.path("target").asText("");
                String code = elem.path("code").asText("");
                String key = target + "::" + code;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("level", elem.path("level").asText(""));
                m.put("category", elem.path("category").asText(""));
                m.put("code", code);
                m.put("target", target);
                m.put("message", elem.path("message").asText(""));
                m.put("suggestion", elem.path("suggestion").isNull() ? null : elem.path("suggestion").asText(""));
                m.put("jumpTarget", elem.path("jumpTarget").isNull() ? null : elem.path("jumpTarget").asText(""));
                indexed.put(key, m);
            }
        } catch (Exception e) {
            log.warn("Failed to index issues: {}", e.getMessage());
        }
        return indexed;
    }

    private Map<String, Object> buildTaskBrief(Task task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("taskNo", task.getTaskNo());
        m.put("name", task.getName());
        m.put("connectionName", task.getConnectionName());
        m.put("endTime", task.getEndTime());
        return m;
    }

    /**
     * 聚合统计:按连接/严重级别/checkId 维度统计最近 N 个诊断任务。
     */
    public Map<String, Object> aggregateStats(Long connectionId, int recentLimit) {
        if (recentLimit <= 0 || recentLimit > 200) recentLimit = 50;
        List<Task> tasks = taskMapper.selectPage(0, recentLimit, null,
                "DIAGNOSTIC", "DIAG_CUSTOM", connectionId, null, null, null, null, null, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analyzedTaskCount", tasks.size());

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        bySeverity.put(DiagnosticIssue.CRITICAL, 0L);
        bySeverity.put(DiagnosticIssue.ERROR, 0L);
        bySeverity.put(DiagnosticIssue.WARNING, 0L);
        bySeverity.put(DiagnosticIssue.INFO, 0L);
        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byCheckId = new LinkedHashMap<>();
        List<Map<String, Object>> timeline = new ArrayList<>();
        long totalIssues = 0;

        for (Task t : tasks) {
            int taskIssues = 0;
            try {
                JsonNode root = MAPPER.readTree(t.getResultSummary());
                JsonNode summaryNode = root.path("summary");
                JsonNode bySevNode = summaryNode.path("bySeverity");
                if (bySevNode.isObject()) {
                    var iter = bySevNode.fields();
                    while (iter.hasNext()) {
                        var e = iter.next();
                        long n = e.getValue().asLong(0);
                        bySeverity.merge(e.getKey(), n, Long::sum);
                        totalIssues += n;
                    }
                }
                JsonNode byCatNode = summaryNode.path("byCategory");
                if (byCatNode.isObject()) {
                    var catIter = byCatNode.fields();
                    while (catIter.hasNext()) {
                        var e = catIter.next();
                        byCategory.merge(e.getKey(), e.getValue().asLong(0), Long::sum);
                    }
                }
                JsonNode perCheck = root.path("perCheckCounts");
                if (perCheck.isObject()) {
                    var checkIter = perCheck.fields();
                    while (checkIter.hasNext()) {
                        var e = checkIter.next();
                        int n = e.getValue().asInt(0);
                        if (n > 0) byCheckId.merge(e.getKey(), (long) n, Long::sum);
                    }
                }
                JsonNode issuesNode = root.path("issues");
                if (issuesNode.isArray()) taskIssues = issuesNode.size();
            } catch (Exception ignored) {}

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("taskNo", t.getTaskNo());
            point.put("name", t.getName());
            point.put("connectionName", t.getConnectionName());
            point.put("endTime", t.getEndTime());
            point.put("issueCount", taskIssues);
            timeline.add(point);
        }

        result.put("totalIssues", totalIssues);
        result.put("bySeverity", bySeverity);
        result.put("byCategory", byCategory);
        result.put("byCheckId", byCheckId);
        result.put("timeline", timeline);
        return result;
    }
}
