package com.trs.modules.diagnostic.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticCheckRegistry;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import com.trs.modules.task.engine.TaskHandler;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.handlers.AbstractTaskHandler;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义诊断任务处理器,任务类型为 DIAG_CUSTOM。
 * <p>
 * 接收用户在诊断工具页面勾选的 checkId 列表,按顺序执行对应的 {@link DiagnosticCheck},
 * 将所有产生的 {@link DiagnosticIssue} 聚合为统一报告写入 task.result_summary。
 * <p>
 * 单条检查抛异常时,生成一条 ERROR 级别的合成问题并继续后续检查,保证一份报告始终能产出。
 *
 * @author czh
 * @date 2026/0728
 */
@Component
public class DiagnosticTaskHandler extends AbstractTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticTaskHandler.class);

    private final DiagnosticCheckRegistry checkRegistry;

    public DiagnosticTaskHandler(SqlgGraphRegistry registry, DiagnosticCheckRegistry checkRegistry) {
        super(registry);
        this.checkRegistry = checkRegistry;
    }

    @Override
    public String taskType() {
        return "DIAG_CUSTOM";
    }

    @Override
    public String buildDisplayName(Task task) {
        return task.getName();
    }

    @Override
    public String execute(TaskContext ctx, ProgressReporter reporter) throws Exception {
        JsonNode params = parseParams(ctx);
        List<String> checkIds = parseCheckIds(params);
        if (checkIds.isEmpty()) {
            throw new IllegalArgumentException("未选择任何诊断检查项");
        }

        List<DiagnosticCheck> checks = checkRegistry.resolve(checkIds);
        if (checks.isEmpty()) {
            throw new IllegalArgumentException("所选检查项均未注册: " + checkIds);
        }

        SqlgGraph graph = graph(ctx);
        List<DiagnosticIssue> allIssues = new ArrayList<>();
        int totalChecks = checks.size();
        int successChecks = 0;
        int failedChecks = 0;
        Map<String, Integer> perCheckCounts = new LinkedHashMap<>();

        if (reporter != null) reporter.report(0, 0, 0, 0, (long) totalChecks, "开始执行 " + totalChecks + " 项诊断");

        for (int i = 0; i < checks.size(); i++) {
            ctx.throwIfCancelled();
            DiagnosticCheck check = checks.get(i);
            String stage = "[" + (i + 1) + "/" + totalChecks + "] " + check.displayName();
            try {
                List<DiagnosticIssue> issues = check.execute(graph, ctx, reporter);
                if (issues == null) issues = List.of();
                allIssues.addAll(issues);
                perCheckCounts.put(check.id(), issues.size());
                successChecks++;
            } catch (TaskContext.TaskCancelledException ce) {
                throw ce;
            } catch (Exception e) {
                log.warn("Diagnostic check {} failed for connection {}", check.id(), ctx.getConnectionId(), e);
                allIssues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, check.category(), "CHECK_EXECUTION_FAILED",
                        check.id(),
                        "检查项 " + check.displayName() + " 执行失败: " + e.getMessage(),
                        "查看后端日志排查具体原因;可能是 SqlgGraph 状态异常或数据库连接问题",
                        null));
                perCheckCounts.put(check.id(), -1);
                failedChecks++;
            }
            try { graph.tx().rollback(); } catch (Exception ignored) {}

            int progress = (int) (100.0 * (i + 1) / totalChecks);
            if (reporter != null) reporter.report(progress, i + 1, successChecks, failedChecks, (long) totalChecks, stage);
        }

        ObjectNode result = buildReport(allIssues, successChecks, failedChecks, totalChecks, perCheckCounts);
        String filePath = writeResultFile(ctx, "diagnostic_report.json", result.toPrettyString());
        result.put("resultFilePath", filePath);

        if (reporter != null) reporter.report(100, totalChecks, successChecks, failedChecks, (long) totalChecks, "完成");
        return result.toString();
    }

    private List<String> parseCheckIds(JsonNode params) {
        List<String> ids = new ArrayList<>();
        if (params != null && params.has("checkIds") && params.get("checkIds").isArray()) {
            for (JsonNode n : params.get("checkIds")) {
                if (!n.isNull()) ids.add(n.asText());
            }
        }
        return ids;
    }

    private ObjectNode buildReport(List<DiagnosticIssue> issues, int successChecks, int failedChecks,
                                     int totalChecks, Map<String, Integer> perCheckCounts) {
        ObjectNode result = result();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        bySeverity.put(DiagnosticIssue.INFO, 0L);
        bySeverity.put(DiagnosticIssue.WARNING, 0L);
        bySeverity.put(DiagnosticIssue.ERROR, 0L);
        bySeverity.put(DiagnosticIssue.CRITICAL, 0L);
        Map<String, Long> byCategory = new LinkedHashMap<>();

        ArrayNode issuesNode = MAPPER.createArrayNode();
        for (DiagnosticIssue issue : issues) {
            ObjectNode m = MAPPER.createObjectNode();
            m.put("level", issue.level());
            m.put("category", issue.category());
            m.put("code", issue.code());
            m.put("target", issue.target());
            m.put("message", issue.message());
            m.put("suggestion", issue.suggestion());
            m.put("jumpTarget", issue.jumpTarget());
            issuesNode.add(m);
            bySeverity.merge(issue.level(), 1L, Long::sum);
            byCategory.merge(issue.category(), 1L, Long::sum);
        }

        ObjectNode summary = MAPPER.createObjectNode();
        summary.put("totalIssues", issues.size());
        ObjectNode severityNode = MAPPER.createObjectNode();
        bySeverity.forEach(severityNode::put);
        summary.set("bySeverity", severityNode);
        ObjectNode categoryNode = MAPPER.createObjectNode();
        byCategory.forEach(categoryNode::put);
        summary.set("byCategory", categoryNode);
        summary.put("totalChecks", totalChecks);
        summary.put("successChecks", successChecks);
        summary.put("failedChecks", failedChecks);
        result.set("summary", summary);
        result.set("issues", issuesNode);

        result.put("successCount", successChecks);
        result.put("failedCount", failedChecks);

        ObjectNode perCheck = MAPPER.createObjectNode();
        perCheckCounts.forEach((k, v) -> perCheck.put(k, v));
        result.set("perCheckCounts", perCheck);

        return result;
    }
}
