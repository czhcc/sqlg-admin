package com.trs.modules.task.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import com.trs.modules.task.entity.Task;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.topology.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统计与诊断任务处理器,覆盖 VertexLabel/EdgeLabel 计数、孤立点统计、
 * Topology 一致性检查、索引检查、数据完整性检查、孤立点检查七种类型。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class StatisticsDiagnosticTaskHandler extends AbstractTaskHandler {

    public StatisticsDiagnosticTaskHandler(SqlgGraphRegistry registry) {
        super(registry);
    }

    @Override
    public String taskType() {
        return "STATISTICS_DIAGNOSTIC";
    }

    @Override
    public String buildDisplayName(Task task) {
        return task.getName();
    }

    @Override
    public String execute(TaskContext ctx, ProgressReporter reporter) throws Exception {
        JsonNode params = parseParams(ctx);
        String type = str(params, "subType");
        report(reporter, 5, 0, 0, 0, null, "初始化");

        return switch (type) {
            case "STAT_VERTEX_COUNTS" -> doVertexCounts(ctx, reporter);
            case "STAT_EDGE_COUNTS" -> doEdgeCounts(ctx, reporter);
            case "STAT_ORPHAN", "DIAG_ORPHAN" -> doOrphanStats(ctx, reporter, type);
            case "DIAG_TOPOLOGY" -> doTopologyCheck(ctx, reporter);
            case "DIAG_INDEX" -> doIndexCheck(ctx, reporter);
            case "DIAG_DATA_INTEGRITY" -> doDataIntegrityCheck(ctx, reporter);
            default -> throw new IllegalArgumentException("未知的统计/诊断子类型: " + type);
        };
    }

    private String doVertexCounts(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var graph = graph(ctx);
        List<Schema> schemas = sortedSchemas(graph.getTopology());
        ObjectNode result = result();
        ArrayNode items = MAPPER.createArrayNode();
        long total = 0;

        List<VertexLabel> allLabels = new ArrayList<>();
        for (Schema s : schemas) allLabels.addAll(s.getVertexLabels().values());
        long totalLabels = allLabels.size();
        int processed = 0;

        for (VertexLabel vl : allLabels) {
            ctx.throwIfCancelled();
            long count = countRows(graph, vl.getSchema().getName(), "V_" + vl.getName());
            ObjectNode item = result();
            item.put("schema", vl.getSchema().getName());
            item.put("label", vl.getName());
            item.put("count", count);
            items.add(item);
            total += count;
            processed++;
            report(reporter, (int) (100.0 * processed / totalLabels), processed, processed, 0, totalLabels, "统计 " + vl.getName());
        }

        result.put("successCount", processed);
        result.put("processedCount", processed);
        result.set("items", items);
        result.put("totalVertices", total);
        String filePath = writeResultFile(ctx, "vertex_counts.json", result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, processed, processed, 0, totalLabels, "完成");
        return result.toString();
    }

    private String doEdgeCounts(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var graph = graph(ctx);
        List<Schema> schemas = sortedSchemas(graph.getTopology());
        ObjectNode result = result();
        ArrayNode items = MAPPER.createArrayNode();
        long total = 0;

        List<EdgeLabel> allLabels = new ArrayList<>();
        for (Schema s : schemas) allLabels.addAll(s.getEdgeLabels().values());
        long totalLabels = allLabels.size();
        int processed = 0;

        for (EdgeLabel el : allLabels) {
            ctx.throwIfCancelled();
            long count = countRows(graph, el.getSchema().getName(), "E_" + el.getName());
            ObjectNode item = result();
            item.put("schema", el.getSchema().getName());
            item.put("label", el.getName());
            item.put("count", count);
            items.add(item);
            total += count;
            processed++;
            report(reporter, (int) (100.0 * processed / totalLabels), processed, processed, 0, totalLabels, "统计 " + el.getName());
        }

        result.put("successCount", processed);
        result.put("processedCount", processed);
        result.set("items", items);
        result.put("totalEdges", total);
        String filePath = writeResultFile(ctx, "edge_counts.json", result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, processed, processed, 0, totalLabels, "完成");
        return result.toString();
    }

    private String doOrphanStats(TaskContext ctx, ProgressReporter reporter, String type) throws Exception {
        var graph = graph(ctx);
        report(reporter, 30, 0, 0, 0, null, "扫描孤立点");
        long orphanCount = graph.traversal().V()
                .not(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.bothE())
                .count().next();
        graph.tx().rollback();

        ObjectNode result = result();
        result.put("orphanCount", orphanCount);
        result.put("successCount", 1);
        result.put("processedCount", 1);
        String fileName = "DIAG_ORPHAN".equals(type) ? "orphan_diagnostic.json" : "orphan_stats.json";
        String filePath = writeResultFile(ctx, fileName, result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, 1, 1, 0, 1L, "完成");
        return result.toString();
    }

    private String doTopologyCheck(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var graph = graph(ctx);
        List<Schema> schemas = sortedSchemas(graph.getTopology());
        ArrayNode issues = MAPPER.createArrayNode();
        int checked = 0;
        int problems = 0;

        List<AbstractLabel> allLabels = new ArrayList<>();
        for (Schema s : schemas) {
            allLabels.addAll(s.getVertexLabels().values());
            allLabels.addAll(s.getEdgeLabels().values());
        }
        long total = allLabels.size();

        for (AbstractLabel label : allLabels) {
            ctx.throwIfCancelled();
            String schemaName = label.getSchema().getName();
            boolean isVertex = label instanceof VertexLabel;
            String prefix = isVertex ? "V_" : "E_";
            boolean exists = tableExists(graph, schemaName, prefix + label.getName());
            if (!exists) {
                ObjectNode issue = result();
                issue.put("level", "ERROR");
                issue.put("type", "MISSING_TABLE");
                issue.put("label", schemaName + "." + label.getName());
                issue.put("message", schemaName + "." + label.getName() + " 在 Topology 中存在,但底层表不存在");
                issues.add(issue);
                problems++;
            }
            checked++;
            report(reporter, (int) (100.0 * checked / total), checked, checked - problems, problems, total, "检查 " + label.getName());
        }

        ObjectNode result = result();
        result.put("checkedCount", checked);
        result.put("problemCount", problems);
        result.put("successCount", checked);
        result.put("failedCount", problems);
        result.set("issues", issues);
        String filePath = writeResultFile(ctx, "topology_check.json", result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, checked, checked, problems, total, "完成");
        return result.toString();
    }

    private String doIndexCheck(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var graph = graph(ctx);
        List<Schema> schemas = sortedSchemas(graph.getTopology());
        ArrayNode items = MAPPER.createArrayNode();
        int largeNoIndex = 0;
        int checked = 0;

        List<VertexLabel> allLabels = new ArrayList<>();
        for (Schema s : schemas) allLabels.addAll(s.getVertexLabels().values());
        long total = allLabels.size();

        for (VertexLabel vl : allLabels) {
            ctx.throwIfCancelled();
            long count = countRows(graph, vl.getSchema().getName(), "V_" + vl.getName());
            boolean hasIndex = !vl.getIndexes().isEmpty();
            boolean hasIdentifier = false;
            try { hasIdentifier = vl.getIdentifiers() != null && !vl.getIdentifiers().isEmpty(); } catch (Exception ignored) {}

            ObjectNode item = result();
            item.put("schema", vl.getSchema().getName());
            item.put("label", vl.getName());
            item.put("count", count);
            item.put("hasIndex", hasIndex);
            item.put("hasIdentifier", hasIdentifier);
            if (count > 10000 && !hasIndex && !hasIdentifier) {
                item.put("warning", "大数据量缺少索引");
                largeNoIndex++;
            }
            items.add(item);
            checked++;
            report(reporter, (int) (100.0 * checked / total), checked, checked, largeNoIndex, total, "检查 " + vl.getName());
        }

        ObjectNode result = result();
        result.put("checkedCount", checked);
        result.put("largeNoIndexCount", largeNoIndex);
        result.put("successCount", checked);
        result.put("failedCount", largeNoIndex);
        result.set("items", items);
        String filePath = writeResultFile(ctx, "index_check.json", result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, checked, checked, largeNoIndex, total, "完成");
        return result.toString();
    }

    private String doDataIntegrityCheck(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var graph = graph(ctx);
        report(reporter, 20, 0, 0, 0, null, "检查边引用完整性");
        long danglingEdges = graph.traversal().E().filter(
                org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.or(
                        org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV().count().is(0),
                        org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV().count().is(0)
                )).count().next();
        graph.tx().rollback();

        report(reporter, 50, 0, 0, 0, null, "统计孤立点");
        long orphanVertices = graph.traversal().V()
                .not(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.bothE())
                .count().next();
        graph.tx().rollback();

        report(reporter, 80, 0, 0, 0, null, "汇总");
        ObjectNode result = result();
        result.put("danglingEdges", danglingEdges);
        result.put("orphanVertices", orphanVertices);
        result.put("successCount", 1);
        result.put("failedCount", danglingEdges > 0 ? 1 : 0);
        String filePath = writeResultFile(ctx, "data_integrity.json", result.toPrettyString());
        result.put("resultFilePath", filePath);
        report(reporter, 100, 1, 1, danglingEdges > 0 ? 1 : 0, 1L, "完成");
        return result.toString();
    }

    private long countRows(org.umlg.sqlg.structure.SqlgGraph graph, String schema, String table) {
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM \"" + schema + "\".\"" + table + "\"")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean tableExists(org.umlg.sqlg.structure.SqlgGraph graph, String schema, String table) {
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.tables WHERE table_schema=? AND table_name=?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }
}
