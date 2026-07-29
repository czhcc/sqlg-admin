package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 索引检查类别下的 2 个检查项实现。
 * <p>
 * 包含:
 * <ul>
 *   <li>{@link LargeTableNoIndexCheck} — 数据量较大的 VertexLabel 既无业务索引也无 identifier</li>
 *   <li>{@link IdentifierMissingIndexCheck} — VertexLabel 配置了 identifier 但对应字段缺少唯一索引</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
final class IndexChecks {

    private IndexChecks() {}

    private static final String CATEGORY = "INDEX";
    private static final long LARGE_TABLE_THRESHOLD = 10_000L;

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
    public static class LargeTableNoIndexCheck implements DiagnosticCheck {

        @Override public String id() { return "index.large_table_no_index"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "大表缺少索引"; }
        @Override public String description() { return "数据量超过 1 万行且既无业务索引也无 identifier 的 VertexLabel"; }
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
                boolean hasIndex = !vl.getIndexes().isEmpty();
                boolean hasIdentifier = false;
                try { hasIdentifier = vl.getIdentifiers() != null && !vl.getIdentifiers().isEmpty(); } catch (Exception ignored) {}
                if (count > LARGE_TABLE_THRESHOLD && !hasIndex && !hasIdentifier) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "LARGE_TABLE_NO_INDEX",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 数据量 " + count + " 超过 " + LARGE_TABLE_THRESHOLD + ",但没有业务索引也没有 identifier",
                            "在属性管理页面为高频查询字段添加索引,或为该 VertexLabel 配置 identifier",
                            "/property-management"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }
    }

    @Component
    public static class IdentifierMissingIndexCheck implements DiagnosticCheck {

        @Override public String id() { return "index.identifier_missing_index"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Identifier 字段缺少唯一索引"; }
        @Override public String description() { return "VertexLabel 配置了 identifier 但底层字段未建立唯一索引(可能存在重复值风险)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                java.util.Set<String> identifiers;
                try {
                    identifiers = vl.getIdentifiers();
                } catch (Exception e) {
                    continue;
                }
                if (identifiers == null || identifiers.isEmpty()) continue;

                boolean hasMatchingIndex = vl.getIndexes().values().stream().anyMatch(idx -> {
                    List<String> idxProps = idx.getProperties() == null ? List.of()
                            : idx.getProperties().stream().map(p -> p.getName()).sorted().toList();
                    List<String> sortedIds = identifiers.stream().sorted().toList();
                    return idxProps.equals(sortedIds);
                });
                if (!hasMatchingIndex) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "IDENTIFIER_MISSING_INDEX",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 的 identifier 字段 " + identifiers + " 未匹配到对应的唯一索引",
                            "为 identifier 字段创建唯一索引,以保证业务标识唯一性并加速查询",
                            "/property-management"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }
}
