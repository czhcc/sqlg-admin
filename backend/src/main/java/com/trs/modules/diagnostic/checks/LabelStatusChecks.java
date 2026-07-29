package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Label 状态检查类别下的 4 个检查项实现。
 * <p>
 * 包含:
 * <ul>
 *   <li>{@link EmptyVertexLabelCheck} — 空 VertexLabel(数据量 = 0)</li>
 *   <li>{@link EmptyEdgeLabelCheck} — 空 EdgeLabel(数据量 = 0)</li>
 *   <li>{@link VertexLabelWithoutPropertiesCheck} — VertexLabel 没有业务属性</li>
 *   <li>{@link EdgeLabelWithoutValidEndpointsCheck} — EdgeLabel 没有有效的出/入点定义</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
final class LabelStatusChecks {

    private LabelStatusChecks() {}

    private static final String CATEGORY = "LABEL_STATUS";

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
    public static class EmptyVertexLabelCheck implements DiagnosticCheck {

        @Override public String id() { return "label.empty_vertex"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "空 VertexLabel"; }
        @Override public String description() { return "VertexLabel 当前没有点数据(0 行)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                long count = DiagnosticSql.countRows(graph, vl.getSchema().getName(), "V_" + vl.getName());
                if (count == 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "EMPTY_VERTEX_LABEL",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 当前没有点数据",
                            "空 Label 本身不是问题;若长期无数据可考虑删除",
                            "/vertex-type"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }
    }

    @Component
    public static class EmptyEdgeLabelCheck implements DiagnosticCheck {

        @Override public String id() { return "label.empty_edge"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "空 EdgeLabel"; }
        @Override public String description() { return "EdgeLabel 当前没有边数据(0 行)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                long count = DiagnosticSql.countRows(graph, el.getSchema().getName(), "E_" + el.getName());
                if (count == 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "EMPTY_EDGE_LABEL",
                            el.getSchema().getName() + "." + el.getName(),
                            el.getSchema().getName() + "." + el.getName() + " 当前没有边数据",
                            "空 Label 本身不是问题;若长期无数据可考虑删除",
                            "/edge-type"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class VertexLabelWithoutPropertiesCheck implements DiagnosticCheck {

        @Override public String id() { return "label.vertex_without_properties"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "VertexLabel 无业务属性"; }
        @Override public String description() { return "VertexLabel 没有定义任何业务属性(只有隐式 ID)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                if (vl.getProperties().isEmpty()) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "VERTEX_WITHOUT_PROPERTIES",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 没有任何业务属性",
                            "为该 VertexLabel 添加必要的标识或业务属性,以便承载实际语义",
                            "/property-management"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class EdgeLabelWithoutValidEndpointsCheck implements DiagnosticCheck {

        @Override public String id() { return "label.edge_without_valid_endpoints"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "EdgeLabel 缺少出入点定义"; }
        @Override public String description() { return "EdgeLabel 未定义有效的 out / in VertexLabel"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                boolean hasOut = !el.getOutVertexLabels().isEmpty();
                boolean hasIn = !el.getInVertexLabels().isEmpty();
                if (!hasOut || !hasIn) {
                    String detail = (!hasOut && !hasIn) ? "未定义 out 和 in VertexLabel"
                            : !hasOut ? "未定义 out VertexLabel" : "未定义 in VertexLabel";
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "EDGE_WITHOUT_VALID_ENDPOINTS",
                            el.getSchema().getName() + "." + el.getName(),
                            el.getSchema().getName() + "." + el.getName() + " " + detail,
                            "在边类型管理页面为该 EdgeLabel 重新指定 out / in VertexLabel",
                            "/edge-type"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }
}
