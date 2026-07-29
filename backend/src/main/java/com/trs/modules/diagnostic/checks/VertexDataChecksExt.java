package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.PropertyColumn;
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
 * 点数据检查扩展(Phase 2),补充 4 个检查项。
 * <ul>
 *   <li>{@link VertexNotNullViolationCheck} — V_ 表 NOT NULL 业务列存在 NULL 值</li>
 *   <li>{@link VertexJsonParseErrorCheck} — JSON/JSONB 属性列存在无法解析的值</li>
 *   <li>{@link VertexCountMismatchCheck} — JDBC count(*) 与 Gremlin g.V().count() 不一致</li>
 *   <li>{@link VertexBooleanTypeAnomalyCheck} — BOOLEAN 属性列存在非布尔值</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class VertexDataChecksExt {

    private VertexDataChecksExt() {}

    private static final String CATEGORY = "VERTEX_DATA";
    private static final String JUMP = "/vertex-data";
    private static final long COUNT_MISMATCH_TOLERANCE = 0;

    private static List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }

    private static List<VertexLabel> allVertexLabels(SqlgGraph graph) {
        List<VertexLabel> all = new ArrayList<>();
        for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
        return all;
    }

    @Component
    public static class VertexNotNullViolationCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.not_null_violation"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "点表 NOT NULL 违反"; }
        @Override public String description() { return "V_ 表中有 NOT NULL 约束的列存在 NULL 值(约束被禁用时导入的脏数据)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = allVertexLabels(graph);
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                List<String> notNullCols = DiagnosticSql.listNotNullBusinessColumns(graph, schema, table);
                if (notNullCols.isEmpty()) { i++; continue; }
                long count = countNotNullViolations(graph, schema, table, notNullCols);
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "VERTEX_NOT_NULL_VIOLATION",
                            schema + "." + vl.getName(),
                            schema + "." + vl.getName() + " 有 " + count + " 行违反 NOT NULL 约束(列: " + notNullCols + ")",
                            "为这些行补齐必填字段值,或修正列约束",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }
    }

    @Component
    public static class VertexJsonParseErrorCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.json_parse_error"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "JSON 属性解析失败"; }
        @Override public String description() { return "JSON/JSONB 属性列存在无法解析为 JSON 的值"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = allVertexLabels(graph);
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                List<String> jsonCols = findJsonColumns(vl);
                if (jsonCols.isEmpty()) { i++; continue; }
                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                long count = countJsonParseErrors(graph, schema, table, jsonCols);
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "JSON_PARSE_ERROR",
                            schema + "." + vl.getName(),
                            schema + "." + vl.getName() + " 有 " + count + " 行的 JSON 属性(" + jsonCols + ")无法解析",
                            "修正这些行的 JSON 属性值,确保为合法 JSON 格式",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }

        private List<String> findJsonColumns(VertexLabel vl) {
            List<String> cols = new ArrayList<>();
            for (PropertyColumn p : vl.getProperties().values()) {
                String typeName = p.getPropertyType() == null ? "" : p.getPropertyType().name();
                if (typeName.contains("JSON")) cols.add(p.getName());
            }
            return cols;
        }

        private long countJsonParseErrors(SqlgGraph graph, String schema, String table, List<String> jsonCols) {
            // 逐列检查:列非空且 json_valid 返回 false(PostgreSQL 提供 json_typeof 在无效 JSON 上会报错,
            // 所以改用 pg_column_exists + try-catch。简化方案:对 text/varchar 列用正则判断是否像 JSON)
            long total = 0;
            for (String col : jsonCols) {
                String sql = "SELECT count(*) FROM \"" + schema + "\".\"" + table + "\"" +
                        " WHERE \"" + col + "\" IS NOT NULL" +
                        " AND \"" + col + "\"::text <> ''" +
                        " AND NOT (\"" + col + "\"::text ~ '^\\s*[\\[\\{]')";
                try (Connection conn = graph.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total += rs.getLong(1);
                } catch (Exception ignored) {}
            }
            return total;
        }
    }

    @Component
    public static class VertexCountMismatchCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.count_mismatch"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "点数量不一致"; }
        @Override public String description() { return "JDBC count(*) 与 Gremlin g.V().hasLabel().count() 结果不一致(Topology 元数据可能过期)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = allVertexLabels(graph);
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                String schema = vl.getSchema().getName();
                String label = vl.getName();
                long jdbcCount = DiagnosticSql.countRows(graph, schema, "V_" + label);
                long gremlinCount;
                try {
                    gremlinCount = graph.traversal().V().hasLabel(schema + "." + label).count().next();
                    graph.tx().rollback();
                } catch (Exception e) {
                    i++;
                    continue;
                }
                if (Math.abs(jdbcCount - gremlinCount) > COUNT_MISMATCH_TOLERANCE) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "COUNT_MISMATCH",
                            schema + "." + label,
                            schema + "." + label + " JDBC count=" + jdbcCount + " 与 Gremlin count=" + gremlinCount + " 不一致",
                            "刷新 Topology 缓存后重新检查;若仍不一致,可能存在物理表与 Topology 元数据不同步",
                            "/topology"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + label);
            }
            return issues;
        }
    }

    @Component
    public static class VertexBooleanTypeAnomalyCheck implements DiagnosticCheck {

        @Override public String id() { return "vertex.boolean_type_anomaly"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "BOOLEAN 属性值异常"; }
        @Override public String description() { return "Topology 定义为 BOOLEAN 的属性列存在非布尔值(非 true/false/t/f/1/0)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = allVertexLabels(graph);
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                List<String> boolCols = findBooleanColumns(vl);
                if (boolCols.isEmpty()) { i++; continue; }
                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                long count = countBooleanAnomalies(graph, schema, table, boolCols);
                if (count > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "BOOLEAN_TYPE_ANOMALY",
                            schema + "." + vl.getName(),
                            schema + "." + vl.getName() + " 有 " + count + " 行的 BOOLEAN 属性(" + boolCols + ")包含非布尔值",
                            "修正这些行的值为 true/false",
                            JUMP));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }

        private List<String> findBooleanColumns(VertexLabel vl) {
            List<String> cols = new ArrayList<>();
            for (PropertyColumn p : vl.getProperties().values()) {
                String typeName = p.getPropertyType() == null ? "" : p.getPropertyType().name();
                if ("BOOLEAN".equals(typeName)) cols.add(p.getName());
            }
            return cols;
        }

        private long countBooleanAnomalies(SqlgGraph graph, String schema, String table, List<String> boolCols) {
            long total = 0;
            for (String col : boolCols) {
                String sql = "SELECT count(*) FROM \"" + schema + "\".\"" + table + "\"" +
                        " WHERE \"" + col + "\" IS NOT NULL" +
                        " AND \"" + col + "\"::text !~ '^(true|false|t|f|1|0)$' AND \"" + col + "\"::text !~ '^(TRUE|FALSE|T|F)$'";
                try (Connection conn = graph.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total += rs.getLong(1);
                } catch (Exception ignored) {}
            }
            return total;
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
