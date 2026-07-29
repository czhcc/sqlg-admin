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
 * Label 状态检查扩展(Phase 2),补充 4 个检查项。
 * <ul>
 *   <li>{@link EdgeLabelWithoutPropertiesCheck} — EdgeLabel 没有业务属性</li>
 *   <li>{@link IsolatedVertexLabelCheck} — VertexLabel 在 Topology 中没有任何关联边</li>
 *   <li>{@link AbnormalLargeVertexLabelCheck} — VertexLabel 数据量异常大(超过阈值)</li>
 *   <li>{@link AbnormalLargeEdgeLabelCheck} — EdgeLabel 数据量异常大(超过阈值)</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class LabelStatusChecksExt {

    private LabelStatusChecksExt() {}

    private static final String CATEGORY = "LABEL_STATUS";
    private static final long ABNORMAL_LARGE_THRESHOLD = 1_000_000L;

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
    public static class EdgeLabelWithoutPropertiesCheck implements DiagnosticCheck {

        @Override public String id() { return "label.edge_without_properties"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "EdgeLabel 无业务属性"; }
        @Override public String description() { return "EdgeLabel 没有定义任何业务属性(仅有隐式端点引用)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                if (el.getProperties().isEmpty()) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "EDGE_WITHOUT_PROPERTIES",
                            el.getSchema().getName() + "." + el.getName(),
                            el.getSchema().getName() + "." + el.getName() + " 没有任何业务属性",
                            "如需要记录边上的业务信息,请通过属性管理页面添加属性",
                            "/property-management"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class IsolatedVertexLabelCheck implements DiagnosticCheck {

        @Override public String id() { return "label.isolated_vertex"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "VertexLabel 无关联边"; }
        @Override public String description() { return "VertexLabel 在 Topology 中没有任何出边或入边定义(孤立点类型)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                boolean hasOutEdges = !vl.getOutEdgeLabels().isEmpty();
                boolean hasInEdges = !vl.getInEdgeLabels().isEmpty();
                if (!hasOutEdges && !hasInEdges) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "ISOLATED_VERTEX_LABEL",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 在 Topology 中没有任何关联边类型",
                            "如该点类型需要参与关系,请在边类型管理中创建连接它的 EdgeLabel",
                            "/edge-type"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class AbnormalLargeVertexLabelCheck implements DiagnosticCheck {

        @Override public String id() { return "label.abnormal_large_vertex"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "数据量异常大的 VertexLabel"; }
        @Override public String description() { return "VertexLabel 数据量超过 " + ABNORMAL_LARGE_THRESHOLD + " 行(可能影响查询性能)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                long count = DiagnosticSql.countRows(graph, vl.getSchema().getName(), "V_" + vl.getName());
                if (count > ABNORMAL_LARGE_THRESHOLD) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "ABNORMAL_LARGE_VERTEX",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 数据量 " + count + " 超过阈值 " + ABNORMAL_LARGE_THRESHOLD,
                            "确认该数据量是否符合业务预期;考虑分区、归档或添加索引以保障查询性能",
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
    public static class AbnormalLargeEdgeLabelCheck implements DiagnosticCheck {

        @Override public String id() { return "label.abnormal_large_edge"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "数据量异常大的 EdgeLabel"; }
        @Override public String description() { return "EdgeLabel 数据量超过 " + ABNORMAL_LARGE_THRESHOLD + " 行(可能影响查询性能)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                long count = DiagnosticSql.countRows(graph, el.getSchema().getName(), "E_" + el.getName());
                if (count > ABNORMAL_LARGE_THRESHOLD) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "ABNORMAL_LARGE_EDGE",
                            el.getSchema().getName() + "." + el.getName(),
                            el.getSchema().getName() + "." + el.getName() + " 数据量 " + count + " 超过阈值 " + ABNORMAL_LARGE_THRESHOLD,
                            "确认该数据量是否符合业务预期;考虑分区、归档或添加索引以保障查询性能",
                            "/edge-type"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }
}
