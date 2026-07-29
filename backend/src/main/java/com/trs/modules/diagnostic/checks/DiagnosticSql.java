package com.trs.modules.diagnostic.checks;

import org.umlg.sqlg.structure.SqlgGraph;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断检查共享的 SQL 工具:表存在性、列存在性、行数、列枚举、物理表枚举。
 * <p>
 * 所有方法吞掉 SQLException 并返回安全默认值(false / 0 / 空列表),
 * 因为诊断检查不应因单条 SQL 失败而中断整个报告生成。
 *
 * @author czh
 * @date 2026/0728
 */
final class DiagnosticSql {

    private DiagnosticSql() {}

    static boolean tableExists(SqlgGraph graph, String schema, String table) {
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

    static boolean columnExists(SqlgGraph graph, String schema, String table, String column) {
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.columns " +
                             "WHERE table_schema=? AND table_name=? AND column_name=?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    static long countRows(SqlgGraph graph, String schema, String table) {
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

    /** 列出指定 schema 下所有以指定前缀开头的物理表名,排除 sqlg 内部表。 */
    static List<String> listPhysicalTables(SqlgGraph graph, String schema, String prefix) {
        List<String> result = new ArrayList<>();
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables " +
                             "WHERE table_schema=? AND table_name LIKE ? " +
                             "ORDER BY table_name")) {
            ps.setString(1, schema);
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** 列出指定 schema 下所有非系统 schema 名(public + 用户自建,排除 sqlg_schema / pg_catalog / information_schema 等)。 */
    static List<String> listUserSchemas(SqlgGraph graph) {
        List<String> result = new ArrayList<>();
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT schema_name FROM information_schema.schemata " +
                             "WHERE schema_name NOT IN ('information_schema','pg_catalog','pg_toast') " +
                             "AND schema_name NOT LIKE 'pg_%' " +
                             "AND schema_name NOT LIKE 'sqlg_%' " +
                             "ORDER BY schema_name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * 列出指定边表的所有 FK 列(命名格式 {@code <vertexSchema>.<VertexLabel>__O} 或 {@code __I}),
     * 返回解析后的结构化信息。用于边数据检查时动态发现端点列。
     */
    static List<FkColumn> listEdgeFkColumns(SqlgGraph graph, String edgeSchema, String edgeTable) {
        List<FkColumn> result = new ArrayList<>();
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns " +
                             "WHERE table_schema=? AND table_name=? " +
                             "AND (column_name LIKE '%\\__O' ESCAPE '\\' OR column_name LIKE '%\\__I' ESCAPE '\\') " +
                             "ORDER BY column_name")) {
            ps.setString(1, edgeSchema);
            ps.setString(2, edgeTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FkColumn parsed = parseFkColumn(rs.getString(1));
                    if (parsed != null) result.add(parsed);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * 列出指定表的所有 NOT NULL 业务列(排除 ID / FK 列 / sqlg 内部列)。
     * 用于检测 NOT NULL 约束违反。
     */
    static List<String> listNotNullBusinessColumns(SqlgGraph graph, String schema, String table) {
        List<String> result = new ArrayList<>();
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns " +
                             "WHERE table_schema=? AND table_name=? AND is_nullable='NO' " +
                             "AND column_name NOT IN ('ID') " +
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

    /**
     * 解析 sqlg 边表 FK 列名:格式 {@code <vSchema>.<vLabel>__O} 或 {@code __I}。
     * 例如 {@code public.Person__O} → vSchema=public, vLabel=Person, direction=OUT。
     */
    static FkColumn parseFkColumn(String columnName) {
        if (columnName == null) return null;
        int dirSep = -1;
        if (columnName.endsWith("__O")) dirSep = columnName.length() - 4;
        else if (columnName.endsWith("__I")) dirSep = columnName.length() - 4;
        else return null;
        if (dirSep <= 0) return null;
        String direction = columnName.endsWith("__O") ? "OUT" : "IN";
        String beforeSuffix = columnName.substring(0, dirSep);
        int dotIdx = beforeSuffix.indexOf('.');
        if (dotIdx <= 0 || dotIdx >= beforeSuffix.length() - 1) return null;
        String vSchema = beforeSuffix.substring(0, dotIdx);
        String vLabel = beforeSuffix.substring(dotIdx + 1);
        return new FkColumn(columnName, vSchema, vLabel, direction, "V_" + vLabel);
    }

    /**
     * FK 列解析结果。
     *
     * @param columnName  原始列名,如 {@code public.Person__O}
     * @param vertexSchema 端点 VertexLabel 所属 schema
     * @param vertexLabel  端点 VertexLabel 名
     * @param direction    OUT 或 IN
     * @param vertexTable  端点物理表名,如 {@code V_Person}
     */
    record FkColumn(String columnName, String vertexSchema, String vertexLabel,
                     String direction, String vertexTable) {}
}
