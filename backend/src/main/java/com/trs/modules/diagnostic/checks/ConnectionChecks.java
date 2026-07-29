package com.trs.modules.diagnostic.checks;

import com.trs.modules.diagnostic.service.DiagnosticCheck;
import com.trs.modules.diagnostic.service.DiagnosticIssue;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * 连接诊断类别下的 2 个检查项实现。
 * <p>
 * 包含:
 * <ul>
 *   <li>{@link JdbcPingCheck} — JDBC 可连通性 + 响应时间</li>
 *   <li>{@link TopologyReadableCheck} — Sqlg Topology 元数据可读取</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
final class ConnectionChecks {

    private ConnectionChecks() {}

    private static final String CATEGORY = "CONNECTION";

    @Component
    public static class JdbcPingCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.jdbc_ping"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "JDBC 连通性"; }
        @Override public String description() { return "测试底层 JDBC 是否可连接并测量响应时间"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            long start = System.currentTimeMillis();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                boolean ok = rs.next();
                long elapsed = System.currentTimeMillis() - start;
                if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
                if (ok) {
                    return List.of(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "JDBC_PING_OK",
                            "jdbc", "JDBC 连接正常,响应时间 " + elapsed + " ms", null, null));
                }
                return List.of(DiagnosticIssue.of(
                        DiagnosticIssue.CRITICAL, CATEGORY, "JDBC_PING_FAILED",
                        "jdbc", "JDBC SELECT 1 未返回结果", "检查数据库服务状态、连接配置和防火墙", null));
            } catch (Exception e) {
                if (reporter != null) reporter.report(100, 1, 0, 1, 1L, "失败");
                return List.of(DiagnosticIssue.of(
                        DiagnosticIssue.CRITICAL, CATEGORY, "JDBC_PING_FAILED",
                        "jdbc", "JDBC 连接失败: " + e.getMessage(),
                        "检查数据库服务状态、连接配置和防火墙", null));
            }
        }
    }

    @Component
    public static class TopologyReadableCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.topology_readable"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Topology 可读"; }
        @Override public String description() { return "Sqlg Topology 元数据是否可正常读取"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            try {
                var topology = graph.getTopology();
                var schemas = topology.getSchemas();
                int schemaCount = schemas.size();
                int vLabelCount = 0;
                for (var s : schemas) vLabelCount += s.getVertexLabels().size();
                if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
                return List.of(DiagnosticIssue.of(
                        DiagnosticIssue.INFO, CATEGORY, "TOPOLOGY_READABLE",
                        "topology", "Topology 元数据读取正常,共 " + schemaCount + " 个 schema," + vLabelCount + " 个 VertexLabel", null, null));
            } catch (Exception e) {
                if (reporter != null) reporter.report(100, 1, 0, 1, 1L, "失败");
                return List.of(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "TOPOLOGY_UNREADABLE",
                        "topology", "Topology 元数据读取失败: " + e.getMessage(),
                        "可能是 sqlg_schema 表损坏或权限不足,检查数据库权限和 sqlg_schema 完整性", null));
            }
        }
    }
}
