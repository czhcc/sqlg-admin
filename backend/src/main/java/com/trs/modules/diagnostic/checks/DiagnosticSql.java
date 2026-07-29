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
}
