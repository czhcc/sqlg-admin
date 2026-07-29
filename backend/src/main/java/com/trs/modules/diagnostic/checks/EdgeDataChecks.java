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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 边数据检查类别下的 7 个检查项实现。
 * <p>
 * sqlg 边表 E_&lt;label&gt; 的物理结构:每行含 {@code ID} 列和若干 FK 列,
 * FK 列命名格式为 {@code <vertexSchema>.<vertexLabel>__O}(出点)或 {@code __I}(入点)。
 * 每条边仅填一组 out+in FK 列,其余为 NULL。
 * <p>
 * 包含 7 个检查:
 * <ul>
 *   <li>{@link DanglingOutVertexCheck} — 出点 FK 指向不存在的点</li>
 *   <li>{@link DanglingInVertexCheck} — 入点 FK 指向不存在的点</li>
 *   <li>{@link EdgeWithoutValidEndpointCheck} — 边行所有 out 或 in FK 列均为 NULL</li>
 *   <li>{@link DuplicateEdgeCheck} — 同一出/入点对存在重复边</li>
 *   <li>{@link EdgeIdentifierEmptyCheck} — 边 identifier 字段为空</li>
 *   <li>{@link EdgeIdentifierDuplicateCheck} — 边 identifier 字段重复</li>
 *   <li>{@link EdgeNotNullViolationCheck} — 边表 NOT NULL 列存在 NULL 值</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class EdgeDataChecks {

    private EdgeDataChecks() {}

    private static final String CATEGORY = "EDGE_DATA";
    private static final String JUMP = "/edge-data";

    private static List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }

    private static List<EdgeLabel> allEdgeLabels(SqlgGraph graph) {
        List<EdgeLabel> all = new ArrayList<>();
        for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
        return all;
    }

    /**
     * 出点 FK 完整性检查:对每条边,LEFT JOIN 出点 V_ 表,统计 V_.ID IS NULL 的行数(即出点不存在)。
     * 因 PostgreSQL FK 约束通常阻止这种情况,该检查主要发现约束被禁用或手工导入的脏数据。
     */
    @Component
    public static class DanglingOutVertexCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.dangling_out_vertex"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "出点不存在的边"; }
        @Override public String description() { return "边的出点 FK 指向的顶点在 V_ 表中不存在(约束被绕过或数据被手工修改)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long count = countDanglingEndpoints(graph, schema, table, "OUT");
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "DANGLING_OUT_VERTEX",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 有 " + count + " 条边的出点在 V_ 表中不存在",
                            "检查 FK 约束是否被禁用;或通过边数据管理清理这些边",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class DanglingInVertexCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.dangling_in_vertex"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "入点不存在的边"; }
        @Override public String description() { return "边的入点 FK 指向的顶点在 V_ 表中不存在"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long count = countDanglingEndpoints(graph, schema, table, "IN");
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "DANGLING_IN_VERTEX",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 有 " + count + " 条边的入点在 V_ 表中不存在",
                            "检查 FK 约束是否被禁用;或通过边数据管理清理这些边",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class EdgeWithoutValidEndpointCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.no_valid_endpoint"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "无有效端点的边"; }
        @Override public String description() { return "边行所有 OUT 或所有 IN FK 列均为 NULL(损坏的边)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long count = countEdgesWithoutEndpoints(graph, schema, table);
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "NO_VALID_ENDPOINT",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 有 " + count + " 条边所有 OUT 或所有 IN FK 列均为 NULL",
                            "这些边数据已损坏,建议直接删除",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class DuplicateEdgeCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.duplicate"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "重复边检查"; }
        @Override public String description() { return "同一 (出点, 入点) 组合存在多条边"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long dupGroups = countDuplicateEdges(graph, schema, table);
                if (dupGroups > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "DUPLICATE_EDGE",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 存在 " + dupGroups + " 组重复边(同一出/入点对)",
                            "排查业务是否允许重复边;若不允许,清理重复边并考虑添加唯一约束",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class EdgeIdentifierEmptyCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.identifier_empty"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "边 Identifier 字段为空"; }
        @Override public String description() { return "EdgeLabel 的 identifier 字段存在 NULL 或空字符串的行"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                Set<String> identifiers;
                try { identifiers = el.getIdentifiers(); } catch (Exception e) { identifiers = null; }
                if (identifiers == null || identifiers.isEmpty()) { i++; continue; }

                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long emptyCount = countEmptyIdentifiersInTable(graph, schema, table, new ArrayList<>(identifiers));
                if (emptyCount > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "EDGE_IDENTIFIER_EMPTY",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 有 " + emptyCount + " 条边的 identifier 字段为空",
                            "为这些边补齐 identifier,或通过边数据管理修正",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class EdgeIdentifierDuplicateCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.identifier_duplicate"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "边 Identifier 字段重复"; }
        @Override public String description() { return "EdgeLabel 的 identifier 字段存在重复值"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                Set<String> identifiers;
                try { identifiers = el.getIdentifiers(); } catch (Exception e) { identifiers = null; }
                if (identifiers == null || identifiers.isEmpty()) { i++; continue; }

                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                long dupGroups = countDuplicateIdentifiersInTable(graph, schema, table, new ArrayList<>(identifiers));
                if (dupGroups > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "EDGE_IDENTIFIER_DUPLICATE",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 的 identifier 字段存在 " + dupGroups + " 组重复值",
                            "排查重复边数据并去重",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    @Component
    public static class EdgeNotNullViolationCheck implements DiagnosticCheck {

        @Override public String id() { return "edge.not_null_violation"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "边表 NOT NULL 违反"; }
        @Override public String description() { return "边表中有 NOT NULL 约束的列存在 NULL 值(约束被禁用时导入的脏数据)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<EdgeLabel> all = allEdgeLabels(graph);
            int i = 0;
            for (EdgeLabel el : all) {
                ctx.throwIfCancelled();
                String schema = el.getSchema().getName();
                String table = "E_" + el.getName();
                List<String> notNullCols = DiagnosticSql.listNotNullBusinessColumns(graph, schema, table);
                if (notNullCols.isEmpty()) { i++; continue; }
                long violationCount = countNotNullViolations(graph, schema, table, notNullCols);
                if (violationCount > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "NOT_NULL_VIOLATION",
                            schema + "." + el.getName(),
                            schema + "." + el.getName() + " 有 " + violationCount + " 行违反 NOT NULL 约束(列: " + notNullCols + ")",
                            "为这些行补齐必填字段值,或修正列约束",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }

    // ============ SQL helpers ============

    private static long countDanglingEndpoints(SqlgGraph graph, String edgeSchema, String edgeTable, String direction) {
        List<DiagnosticSql.FkColumn> fkCols = DiagnosticSql.listEdgeFkColumns(graph, edgeSchema, edgeTable);
        long total = 0;
        for (DiagnosticSql.FkColumn fk : fkCols) {
            if (!fk.direction().equals(direction)) continue;
            String sql = "SELECT count(*) FROM \"" + edgeSchema + "\".\"" + edgeTable + "\" e " +
                    "LEFT JOIN \"" + fk.vertexSchema() + "\".\"" + fk.vertexTable() + "\" v " +
                    "ON e.\"" + fk.columnName() + "\" = v.\"ID\" " +
                    "WHERE e.\"" + fk.columnName() + "\" IS NOT NULL AND v.\"ID\" IS NULL";
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getLong(1);
            } catch (Exception ignored) {}
        }
        return total;
    }

    private static long countEdgesWithoutEndpoints(SqlgGraph graph, String edgeSchema, String edgeTable) {
        List<DiagnosticSql.FkColumn> fkCols = DiagnosticSql.listEdgeFkColumns(graph, edgeSchema, edgeTable);
        List<String> outCols = fkCols.stream().filter(f -> f.direction().equals("OUT")).map(DiagnosticSql.FkColumn::columnName).toList();
        List<String> inCols = fkCols.stream().filter(f -> f.direction().equals("IN")).map(DiagnosticSql.FkColumn::columnName).toList();
        if (outCols.isEmpty() || inCols.isEmpty()) return 0;
        StringBuilder where = new StringBuilder("WHERE ");
        where.append("(");
        for (int j = 0; j < outCols.size(); j++) {
            if (j > 0) where.append(" AND ");
            where.append("\"").append(outCols.get(j)).append("\" IS NULL");
        }
        where.append(") OR (");
        for (int j = 0; j < inCols.size(); j++) {
            if (j > 0) where.append(" AND ");
            where.append("\"").append(inCols.get(j)).append("\" IS NULL");
        }
        where.append(")");
        String sql = "SELECT count(*) FROM \"" + edgeSchema + "\".\"" + edgeTable + "\" " + where;
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long countDuplicateEdges(SqlgGraph graph, String edgeSchema, String edgeTable) {
        List<DiagnosticSql.FkColumn> fkCols = DiagnosticSql.listEdgeFkColumns(graph, edgeSchema, edgeTable);
        List<String> outCols = fkCols.stream().filter(f -> f.direction().equals("OUT")).map(DiagnosticSql.FkColumn::columnName).toList();
        List<String> inCols = fkCols.stream().filter(f -> f.direction().equals("IN")).map(DiagnosticSql.FkColumn::columnName).toList();
        if (outCols.isEmpty() || inCols.isEmpty()) return 0;

        // COALESCE 所有 OUT 列为一个值,所有 IN 列为一个值,然后按 (out, in) 分组找重复
        StringBuilder outCoalesce = buildCoalesce(outCols);
        StringBuilder inCoalesce = buildCoalesce(inCols);
        String sql = "SELECT count(*) FROM (" +
                "SELECT " + outCoalesce + " AS out_id, " + inCoalesce + " AS in_id " +
                "FROM \"" + edgeSchema + "\".\"" + edgeTable + "\" " +
                "WHERE " + outCoalesce + " IS NOT NULL AND " + inCoalesce + " IS NOT NULL " +
                "GROUP BY out_id, in_id HAVING count(*) > 1" +
                ") d";
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static StringBuilder buildCoalesce(List<String> cols) {
        StringBuilder sb = new StringBuilder("COALESCE(");
        for (int j = 0; j < cols.size(); j++) {
            if (j > 0) sb.append(", ");
            sb.append("\"").append(cols.get(j)).append("\"");
        }
        sb.append(")");
        return sb;
    }

    private static long countEmptyIdentifiersInTable(SqlgGraph graph, String schema, String table, List<String> identifiers) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM \"").append(schema).append("\".\"").append(table).append("\" WHERE ");
        for (int j = 0; j < identifiers.size(); j++) {
            if (j > 0) sql.append(" OR ");
            String col = identifiers.get(j);
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

    private static long countDuplicateIdentifiersInTable(SqlgGraph graph, String schema, String table, List<String> identifiers) {
        StringBuilder selectCols = new StringBuilder();
        for (int j = 0; j < identifiers.size(); j++) {
            if (j > 0) selectCols.append(", ");
            selectCols.append("\"").append(identifiers.get(j)).append("\"");
        }
        String sql = "SELECT count(*) FROM (" +
                "SELECT " + selectCols + ", count(*) FROM \"" + schema + "\".\"" + table + "\"" +
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

    private static long countNotNullViolations(SqlgGraph graph, String schema, String table, List<String> notNullCols) {
        StringBuilder where = new StringBuilder("WHERE ");
        for (int j = 0; j < notNullCols.size(); j++) {
            if (j > 0) where.append(" OR ");
            where.append("\"").append(notNullCols.get(j)).append("\" IS NULL");
        }
        String sql = "SELECT count(*) FROM \"" + schema + "\".\"" + table + "\" " + where;
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
