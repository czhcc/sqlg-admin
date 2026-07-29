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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Topology 一致性检查扩展(Phase 2),补充 3 个检查项。
 * <ul>
 *   <li>{@link OrphanColumnCheck} — 底层字段存在但 Topology 中无对应属性</li>
 *   <li>{@link PropertyTypeMismatchCheck} — Topology 属性类型与数据库字段类型不一致</li>
 *   <li>{@link TopologyIndexDbMismatchCheck} — Topology 定义的索引在数据库中缺失</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class TopologyChecksExt {

    private TopologyChecksExt() {}

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
    public static class OrphanColumnCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.orphan_column"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "底层冗余字段"; }
        @Override public String description() { return "底层表存在字段,但 Topology 中没有对应属性(排除 ID 和 FK 列)"; }
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
                List<String> dbColumns = listBusinessColumns(graph, schema, table);
                List<String> topoProps = label.getProperties().values().stream()
                        .map(PropertyColumn::getName).toList();
                for (String col : dbColumns) {
                    if (!topoProps.contains(col)) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.WARNING, CATEGORY, "ORPHAN_COLUMN",
                                schema + "." + table + "." + col,
                                "底层表 " + schema + "." + table + " 的字段 " + col + " 在 Topology 中没有对应属性",
                                "可能是手工添加的字段;建议通过属性管理页面同步到 Topology,或从数据库删除",
                                "/property-management"));
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }

        private List<String> listBusinessColumns(SqlgGraph graph, String schema, String table) {
            List<String> result = new ArrayList<>();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT column_name FROM information_schema.columns " +
                                 "WHERE table_schema=? AND table_name=? " +
                                 "AND column_name <> 'ID' " +
                                 "AND column_name NOT LIKE '%\\__O' ESCAPE '\\' " +
                                 "AND column_name NOT LIKE '%\\__I' ESCAPE '\\' " +
                                 "ORDER BY column_name")) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(rs.getString(1));
                }
            } catch (Exception ignored) {}
            return result;
        }
    }

    @Component
    public static class PropertyTypeMismatchCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.property_type_mismatch"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "属性类型不一致"; }
        @Override public String description() { return "Topology 定义的属性类型与数据库字段类型不匹配"; }
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
                Map<String, String> dbTypes = loadColumnTypes(graph, schema, table);
                for (PropertyColumn p : label.getProperties().values()) {
                    ctx.throwIfCancelled();
                    String dbType = dbTypes.get(p.getName());
                    if (dbType == null) continue;
                    String topoType = p.getPropertyType() == null ? "" : p.getPropertyType().name().toLowerCase();
                    if (!isTypeCompatible(topoType, dbType.toLowerCase())) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.WARNING, CATEGORY, "PROPERTY_TYPE_MISMATCH",
                                schema + "." + table + "." + p.getName(),
                                schema + "." + label.getName() + "." + p.getName()
                                        + " Topology 类型=" + topoType + " 但 DB 类型=" + dbType,
                                "统一 Topology 和数据库的类型定义;类型不一致可能导致查询或写入异常",
                                "/property-management"));
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }

        private Map<String, String> loadColumnTypes(SqlgGraph graph, String schema, String table) {
            Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT column_name, data_type FROM information_schema.columns " +
                                 "WHERE table_schema=? AND table_name=?")) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) map.put(rs.getString(1), rs.getString(2));
                }
            } catch (Exception ignored) {}
            return map;
        }

        private boolean isTypeCompatible(String topoType, String dbType) {
            if (topoType.isEmpty() || dbType.isEmpty()) return true;
            return switch (topoType) {
                case "string" -> dbType.contains("char") || dbType.contains("text");
                case "integer", "int", "long" -> dbType.contains("int") || dbType.contains("serial");
                case "float", "double", "decimal" -> dbType.contains("float") || dbType.contains("double")
                        || dbType.contains("numeric") || dbType.contains("decimal");
                case "boolean" -> dbType.contains("bool");
                case "json" -> dbType.contains("json");
                case "byte" -> dbType.contains("bytea") || dbType.contains("binary");
                case "localdate", "localtime", "localdatetime", "zoneddatetime" ->
                        dbType.contains("timestamp") || dbType.contains("date") || dbType.contains("time");
                default -> true;
            };
        }
    }

    @Component
    public static class TopologyIndexDbMismatchCheck implements DiagnosticCheck {

        @Override public String id() { return "topology.index_db_missing"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Topology 索引数据库缺失"; }
        @Override public String description() { return "Topology 中定义的索引在数据库 pg_indexes 中不存在"; }
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
                List<String> dbIndexNames = listDbIndexNames(graph, schema, table);
                for (var idx : label.getIndexes().values()) {
                    ctx.throwIfCancelled();
                    if (!dbIndexNames.contains(idx.getName())) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.ERROR, CATEGORY, "INDEX_DB_MISSING",
                                schema + "." + table + "." + idx.getName(),
                                "Topology 索引 " + idx.getName() + " 在数据库表 " + schema + "." + table + " 中不存在",
                                "通过属性管理页面重建该索引",
                                "/property-management"));
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }

        private List<String> listDbIndexNames(SqlgGraph graph, String schema, String table) {
            List<String> result = new ArrayList<>();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT indexname FROM pg_indexes WHERE schemaname=? AND tablename=?")) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(rs.getString(1));
                }
            } catch (Exception ignored) {}
            return result;
        }
    }
}
