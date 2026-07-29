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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 点数据检查类别下的 3 个检查项实现。
 * <p>
 * 包含:
 * <ul>
 *   <li>{@link OrphanVerticesCheck} — 全图孤立点统计(g.V().not(bothE()))</li>
 *   <li>{@link IdentifierEmptyCheck} — VertexLabel 的 identifier 字段为空(NULL 或 '')的行数</li>
 *   <li>{@link IdentifierDuplicateCheck} — VertexLabel 的 identifier 字段存在重复值</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
final class VertexDataChecks {

    private VertexDataChecks() {}

    private static final String CATEGORY = "VERTEX_DATA";
    private static final String JUMP = "/vertex-data";

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
    public static class OrphanVerticesCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.orphan_count"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "孤立点统计"; }
        @Override public String description() { return "全图没有任何边连接的点数量(可能为脏数据)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            if (reporter != null) reporter.report(20, 0, 0, 0, null, "扫描孤立点");
            long orphanCount;
            try {
                orphanCount = graph.traversal().V()
                        .not(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.bothE())
                        .count().next();
                graph.tx().rollback();
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "ORPHAN_SCAN_FAILED",
                        "global", "孤立点扫描失败: " + e.getMessage(), null, JUMP));
                return issues;
            }
            if (orphanCount > 0) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.WARNING, CATEGORY, "ORPHAN_VERTICES",
                        "global",
                        "全图共 " + orphanCount + " 个孤立点(没有任何边连接)",
                        "检查这些点是否应该建立关系,或通过点数据管理清理",
                        JUMP));
            } else {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.INFO, CATEGORY, "ORPHAN_VERTICES",
                        "global", "全图无孤立点", null, JUMP));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class IdentifierEmptyCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.identifier_empty"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Identifier 字段为空"; }
        @Override public String description() { return "VertexLabel 的 identifier 字段存在 NULL 或空字符串的行"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                java.util.Set<String> identifiers;
                try { identifiers = vl.getIdentifiers(); } catch (Exception e) { identifiers = null; }
                if (identifiers == null || identifiers.isEmpty()) { i++; continue; }

                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                long emptyCount = countEmptyIdentifiers(graph, schema, table, identifiers);
                if (emptyCount > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "IDENTIFIER_EMPTY",
                            schema + "." + vl.getName(),
                            schema + "." + vl.getName() + " 有 " + emptyCount + " 行的 identifier 字段(" + identifiers + ")为空",
                            "为这些行补齐 identifier,或通过点数据管理修正",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }

        private long countEmptyIdentifiers(SqlgGraph graph, String schema, String table, java.util.Set<String> identifiers) {
            List<String> idList = new ArrayList<>(identifiers);
            StringBuilder sql = new StringBuilder("SELECT count(*) FROM \"").append(schema).append("\".\"").append(table).append("\" WHERE ");
            for (int j = 0; j < idList.size(); j++) {
                if (j > 0) sql.append(" OR ");
                String col = idList.get(j);
                sql.append("(\"").append(col).append("\" IS NULL OR \"").append(col).append("\"::text = '')");
            }
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString());
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    @Component
    public static class IdentifierDuplicateCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.identifier_duplicate"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Identifier 字段重复"; }
        @Override public String description() { return "VertexLabel 的 identifier 字段存在重复值"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                java.util.Set<String> identifiers;
                try { identifiers = vl.getIdentifiers(); } catch (Exception e) { identifiers = null; }
                if (identifiers == null || identifiers.isEmpty()) { i++; continue; }

                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                long dupGroups = countDuplicateGroups(graph, schema, table, identifiers);
                if (dupGroups > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "IDENTIFIER_DUPLICATE",
                            schema + "." + vl.getName(),
                            schema + "." + vl.getName() + " 的 identifier 字段(" + identifiers + ")存在 " + dupGroups + " 组重复值",
                            "排查重复数据并去重,然后为 identifier 字段添加唯一索引防止再次出现",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }

        private long countDuplicateGroups(SqlgGraph graph, String schema, String table, java.util.Set<String> identifiers) {
            List<String> idList = new ArrayList<>(identifiers);
            StringBuilder selectCols = new StringBuilder();
            for (int j = 0; j < idList.size(); j++) {
                if (j > 0) selectCols.append(", ");
                selectCols.append("\"").append(idList.get(j)).append("\"");
            }
            StringBuilder whereNotNull = new StringBuilder();
            for (int j = 0; j < idList.size(); j++) {
                if (j > 0) whereNotNull.append(" AND ");
                whereNotNull.append("\"").append(idList.get(j)).append("\" IS NOT NULL");
            }
            String sql = "SELECT count(*) FROM (" +
                    "SELECT " + selectCols + ", count(*) FROM \"" + schema + "\".\"" + table + "\"" +
                    (whereNotNull.length() > 0 ? " WHERE " + whereNotNull : "") +
                    " GROUP BY " + selectCols + " HAVING count(*) > 1" +
                    ") d";
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (Exception e) {
                return 0;
            }
        }
    }
}
