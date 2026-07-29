package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Index;
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
 * 索引检查扩展(Phase 2),补充 5 个检查项。
 * <ul>
 *   <li>{@link UniqueIndexDuplicateCheck} — UNIQUE 索引字段存在重复数据</li>
 *   <li>{@link IdentifierWithoutAnyIndexCheck} — identifier 字段完全没有索引(连普通索引都没有)</li>
 *   <li>{@link DbIndexTopologyMissingCheck} — DB 有索引但 Topology 中未记录</li>
 *   <li>{@link RedundantIndexCheck} — 冗余索引(一个索引的列是另一个索引的前缀)</li>
 *   <li>{@link EdgeLargeNoIndexCheck} — 大数据量 EdgeLabel 无业务索引</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class IndexChecksExt {

    private IndexChecksExt() {}

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
    public static class UniqueIndexDuplicateCheck implements DiagnosticCheck {

        @Override public String id() { return "index.unique_duplicate_values"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "UNIQUE 索引重复值"; }
        @Override public String description() { return "UNIQUE 索引字段存在重复数据(索引可能被禁用时插入)"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            int i = 0;
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                for (var idx : vl.getIndexes().values()) {
                    ctx.throwIfCancelled();
                    String idxType = idx.getIndexType() == null ? "" : idx.getIndexType().getName();
                    if (!idxType.contains("UNIQUE")) continue;
                    List<String> cols = idx.getProperties().stream().map(p -> p.getName()).toList();
                    long dups = countDuplicates(graph, schema, table, cols);
                    if (dups > 0) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.ERROR, CATEGORY, "UNIQUE_DUPLICATE",
                                schema + "." + vl.getName() + "." + idx.getName(),
                                schema + "." + vl.getName() + " 的 UNIQUE 索引 " + idx.getName()
                                        + " (列: " + cols + ") 存在 " + dups + " 组重复值",
                                "排查并清除重复数据,确保 UNIQUE 约束生效",
                                "/vertex-data"));
                    }
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + vl.getName());
            }
            return issues;
        }

        private long countDuplicates(SqlgGraph graph, String schema, String table, List<String> cols) {
            StringBuilder selectCols = new StringBuilder();
            for (int j = 0; j < cols.size(); j++) {
                if (j > 0) selectCols.append(", ");
                selectCols.append("\"").append(cols.get(j)).append("\"");
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
    }

    @Component
    public static class IdentifierWithoutAnyIndexCheck implements DiagnosticCheck {

        @Override public String id() { return "index.identifier_no_any_index"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Identifier 完全无索引"; }
        @Override public String description() { return "VertexLabel 的 identifier 字段既无业务索引也无唯一索引"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                java.util.Set<String> identifiers;
                try { identifiers = vl.getIdentifiers(); } catch (Exception e) { identifiers = null; }
                if (identifiers == null || identifiers.isEmpty()) continue;
                final java.util.Set<String> finalIds = identifiers;
                boolean anyIndexOnId = vl.getIndexes().values().stream().anyMatch(idx ->
                        idx.getProperties().stream().anyMatch(p -> finalIds.contains(p.getName())));
                if (!anyIndexOnId) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "IDENTIFIER_NO_INDEX",
                            vl.getSchema().getName() + "." + vl.getName(),
                            vl.getSchema().getName() + "." + vl.getName() + " 的 identifier 字段 " + identifiers + " 完全没有索引",
                            "为 identifier 字段添加索引以加速查询和保证唯一性",
                            "/property-management"));
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class DbIndexTopologyMissingCheck implements DiagnosticCheck {

        @Override public String id() { return "index.db_index_topology_missing"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "DB 冗余索引"; }
        @Override public String description() { return "数据库有索引但 Topology 中未记录(可能是手工创建的索引)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                String schema = vl.getSchema().getName();
                String table = "V_" + vl.getName();
                if (!DiagnosticSql.tableExists(graph, schema, table)) continue;
                List<DbIndexInfo> dbIndexes = listDbIndexes(graph, schema, table);
                List<String> topoIndexNames = vl.getIndexes().values().stream()
                        .map(Index::getName).toList();
                for (DbIndexInfo di : dbIndexes) {
                    if (di.name.equalsIgnoreCase(table + "_pkey")) continue;
                    if (!topoIndexNames.contains(di.name)) {
                        issues.add(DiagnosticIssue.of(
                                DiagnosticIssue.INFO, CATEGORY, "DB_INDEX_TOPOLOGY_MISSING",
                                schema + "." + table + "." + di.name,
                                "数据库索引 " + di.name + " 存在于 " + schema + "." + table + " 但 Topology 中未记录",
                                "如需在管理界面可见,通过属性管理页面同步;否则可忽略",
                                "/property-management"));
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }

        private List<DbIndexInfo> listDbIndexes(SqlgGraph graph, String schema, String table) {
            List<DbIndexInfo> result = new ArrayList<>();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT indexname FROM pg_indexes WHERE schemaname=? AND tablename=?")) {
                ps.setString(1, schema);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(new DbIndexInfo(rs.getString(1)));
                }
            } catch (Exception ignored) {}
            return result;
        }

        private record DbIndexInfo(String name) {}
    }

    @Component
    public static class RedundantIndexCheck implements DiagnosticCheck {

        @Override public String id() { return "index.redundant"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "冗余索引"; }
        @Override public String description() { return "一个索引的列是另一个多列索引的前缀(可删除冗余索引减少写入开销)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<VertexLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getVertexLabels().values());
            for (VertexLabel vl : all) {
                ctx.throwIfCancelled();
                var indexList = new ArrayList<>(vl.getIndexes().values());
                for (int a = 0; a < indexList.size(); a++) {
                    ctx.throwIfCancelled();
                    List<String> colsA = indexList.get(a).getProperties().stream()
                            .map(p -> p.getName()).toList();
                    if (colsA.isEmpty()) continue;
                    for (int b = 0; b < indexList.size(); b++) {
                        if (a == b) continue;
                        List<String> colsB = indexList.get(b).getProperties().stream()
                                .map(p -> p.getName()).toList();
                        if (colsA.size() < colsB.size() && isPrefix(colsA, colsB)) {
                            issues.add(DiagnosticIssue.of(
                                    DiagnosticIssue.INFO, CATEGORY, "REDUNDANT_INDEX",
                                    vl.getSchema().getName() + "." + vl.getName() + "." + indexList.get(a).getName(),
                                    vl.getSchema().getName() + "." + vl.getName() + " 索引 " + indexList.get(a).getName()
                                            + " (列: " + colsA + ") 是索引 " + indexList.get(b).getName()
                                            + " (列: " + colsB + ") 的前缀,可能是冗余索引",
                                    "评估是否可以删除冗余索引以减少写入开销",
                                    "/property-management"));
                        }
                    }
                }
            }
            if (reporter != null) reporter.report(100, 1, 1, issues.size(), 1L, "完成");
            return issues;
        }

        private boolean isPrefix(List<String> shortCols, List<String> longCols) {
            if (shortCols.size() > longCols.size()) return false;
            for (int i = 0; i < shortCols.size(); i++) {
                if (!shortCols.get(i).equals(longCols.get(i))) return false;
            }
            return true;
        }
    }

    @Component
    public static class EdgeLargeNoIndexCheck implements DiagnosticCheck {

        @Override public String id() { return "index.edge_large_no_index"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "大边表缺少索引"; }
        @Override public String description() { return "数据量超过 " + LARGE_TABLE_THRESHOLD + " 且无业务索引的 EdgeLabel"; }
        @Override public boolean isHeavy() { return true; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            List<org.umlg.sqlg.structure.topology.EdgeLabel> all = new ArrayList<>();
            for (Schema s : sortedSchemas(graph.getTopology())) all.addAll(s.getEdgeLabels().values());
            int i = 0;
            for (org.umlg.sqlg.structure.topology.EdgeLabel el : all) {
                ctx.throwIfCancelled();
                long count = DiagnosticSql.countRows(graph, el.getSchema().getName(), "E_" + el.getName());
                boolean hasIndex = !el.getIndexes().isEmpty();
                if (count > LARGE_TABLE_THRESHOLD && !hasIndex) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "EDGE_LARGE_NO_INDEX",
                            el.getSchema().getName() + "." + el.getName(),
                            el.getSchema().getName() + "." + el.getName() + " 数据量 " + count + " 超过 " + LARGE_TABLE_THRESHOLD + ",但无业务索引",
                            "为高频查询的边属性添加索引",
                            "/property-management"));
                }
                i++;
                if (reporter != null) reporter.report(
                        (int) (100.0 * i / all.size()), i, i - issues.size(), issues.size(), (long) all.size(), "检查 " + el.getName());
            }
            return issues;
        }
    }
}
