package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Topology 一致性检查类别下的 4 个检查项实现。
 * <p>
 * 包含:
 * <ul>
 *   <li>{@link LabelMissingTableCheck} — Topology 中存在 Label 但底层 V_/E_ 表不存在</li>
 *   <li>{@link OrphanVertexTableCheck} — 底层存在 V_ 表但 Topology 中无对应 VertexLabel</li>
 *   <li>{@link OrphanEdgeTableCheck} — 底层存在 E_ 表但 Topology 中无对应 EdgeLabel</li>
 *   <li>{@link PropertyMissingColumnCheck} — Topology 属性存在但底层字段不存在</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
final class TopologyChecks {

    private TopologyChecks() {}

    private static final String CATEGORY = "TOPOLOGY";

    private static List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }

    @Component
    public static class LabelMissingTableCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.label_missing_table"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Label 对应底层表缺失"; }
        @Override public String description() { return "Topology 中存在 VertexLabel/EdgeLabel,但底层 V_/E_ 物理表不存在"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<AbstractLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) {
                all.addAll(s.getVertexLabels().values());
                all.addAll(s.getEdgeLabels().values());
            }
            int i = 0;
            for (AbstractLabel label : all) {
                ctx.throwIfCancelled();
                String schema = label.getSchema().getName();
                String prefix = label instanceof VertexLabel ? "V_" : "E_";
                String table = prefix + label.getName();
                if (!DiagnosticSql.tableExists(graph, schema, table)) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.CRITICAL, CATEGORY, "LABEL_MISSING_TABLE",
                            schema + "." + label.getName(),
                            schema + "." + label.getName() + " 在 Topology 中存在,但底层表 " + schema + "." + table + " 不存在",
                            "检查数据库是否被手工删除过表;或重建该 Label 以恢复物理表结构",
                            "/topology"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + label.getName());
            }
            return issues;
        }
    }

    @Component
    public static class OrphanVertexTableCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.orphan_vertex_table"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "孤立 V_ 物理表"; }
        @Override public String description() { return "底层存在 V_ 表,但 Topology 中无对应 VertexLabel"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) {
                ctx.throwIfCancelled();
                String schemaName = s.getName();
                List<String> orphanTables = findOrphanTables(graph, s, "V_", s.getVertexLabels().keySet());
                for (String t : orphanTables) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "ORPHAN_VERTEX_TABLE",
                            schemaName + "." + t,
                            "底层表 " + schemaName + "." + t + " 存在,但 Topology 中没有对应 VertexLabel",
                            "可能是手工建表或 Topology 元数据损坏;建议清理该物理表或重建对应 VertexLabel",
                            "/topology"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class OrphanEdgeTableCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.orphan_edge_table"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "孤立 E_ 物理表"; }
        @Override public String description() { return "底层存在 E_ 表,但 Topology 中无对应 EdgeLabel"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) {
                ctx.throwIfCancelled();
                String schemaName = s.getName();
                List<String> orphanTables = findOrphanTables(graph, s, "E_", s.getEdgeLabels().keySet());
                for (String t : orphanTables) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "ORPHAN_EDGE_TABLE",
                            schemaName + "." + t,
                            "底层表 " + schemaName + "." + t + " 存在,但 Topology 中没有对应 EdgeLabel",
                            "可能是手工建表或 Topology 元数据损坏;建议清理该物理表或重建对应 EdgeLabel",
                            "/topology"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class PropertyMissingColumnCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.property_missing_column"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Topology 属性对应字段缺失"; }
        @Override public String description() { return "Topology 中定义了属性,但底层表对应字段不存在"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<AbstractLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) {
                all.addAll(s.getVertexLabels().values());
                all.addAll(s.getEdgeLabels().values());
            }
            for (AbstractLabel label : all) {
                ctx.throwIfCancelled();
                String schema = label.getSchema().getName();
                String prefix = label instanceof VertexLabel ? "V_" : "E_";
                String table = prefix + label.getName();
                if (!DiagnosticSql.tableExists(graph, schema, table)) continue;
                for (PropertyColumn p : label.getProperties().values()) {
                    ctx.throwIfCancelled();
                    String col = p.getName();
                    if (!DiagnosticSql.columnExists(graph, schema, table, col)) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.ERROR, CATEGORY, "PROPERTY_MISSING_COLUMN",
                                schema + "." + table + "." + col,
                                "Topology 属性 " + schema + "." + label.getName() + "." + col + " 在底层表 " + table + " 中没有对应字段",
                                "可能是手工删除了字段;建议通过属性管理页面重建该属性",
                                "/property-management"));
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    private static List<String> findOrphanTables(SqlgGraph graph, Schema schema, String prefix, java.util.Set<String> labelNames) {
        List<String> orphans = new ArrayList<>();
        for (String table : DiagnosticSql.listPhysicalTables(graph, schema.getName(), prefix)) {
            String labelName = table.substring(prefix.length());
            if (!labelNames.contains(labelName)) orphans.add(table);
        }
        return orphans;
    }
}
