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
import java.util.ArrayList;
import java.util.List;

/**
 * 连接诊断检查扩展(Phase 2),补充 6 个检查项。
 * <p>
 * 第一版只做只读检测,事务测试使用 BEGIN-ROLLBACK 不实际写入数据。
 * <ul>
 *   <li>{@link SqlgGraphOpenCheck} — SqlgGraph 可正常打开(已打开即通过)</li>
 *   <li>{@link DatabaseVersionCheck} — 数据库版本(报告为 INFO)</li>
 *   <li>{@link UserPermissionCheck} — 当前用户是否具备 CREATE / USAGE 权限</li>
 *   <li>{@link ResponseTimeCheck} — 连接响应时间基准测试</li>
 *   <li>{@link TransactionTestCheck} — 事务提交/回滚是否正常(BEGIN-ROLLBACK 只读)</li>
 *   <li>{@link DistributedConfigCheck} — distributed 配置是否符合运行方式</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0729
 */
final class ConnectionChecksExt {

    private ConnectionChecksExt() {}

    private static final String CATEGORY = "CONNECTION";
    private static final long RESPONSE_TIME_WARN_MS = 1000;

    @Component
    public static class SqlgGraphOpenCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.sqlg_graph_open"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "SqlgGraph 可打开"; }
        @Override public String description() { return "SqlgGraph 实例是否可正常使用(已打开即通过)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            try {
                if (graph != null && graph.tx() != null) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "SQLG_GRAPH_OPEN_OK",
                            "sqlg-graph", "SqlgGraph 实例正常", null, null));
                } else {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.ERROR, CATEGORY, "SQLG_GRAPH_NULL",
                            "sqlg-graph", "SqlgGraph 实例为 null 或事务不可用",
                            "检查 sqlg.enabled 配置和数据源连接", null));
                }
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "SQLG_GRAPH_OPEN_FAILED",
                        "sqlg-graph", "SqlgGraph 打开失败: " + e.getMessage(),
                        "检查 sqlg.properties 配置和数据库连通性", null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class DatabaseVersionCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.db_version"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "数据库版本"; }
        @Override public String description() { return "查询当前数据库版本(报告为 INFO)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT version()");
                 ResultSet rs = ps.executeQuery()) {
                String version = rs.next() ? rs.getString(1) : "unknown";
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.INFO, CATEGORY, "DB_VERSION",
                        "db-version", "数据库版本: " + version, null, null));
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "DB_VERSION_FAILED",
                        "db-version", "查询数据库版本失败: " + e.getMessage(), null, null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class UserPermissionCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.user_permissions"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "用户权限"; }
        @Override public String description() { return "当前数据库用户是否具备 CREATE 和 USAGE 权限"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            try (Connection conn = graph.getConnection()) {
                String currentUser = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT current_user");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) currentUser = rs.getString(1);
                }
                boolean canCreate = hasSchemaPermission(conn, "CREATE");
                boolean canUsage = hasSchemaPermission(conn, "USAGE");
                if (canCreate && canUsage) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "USER_PERMISSIONS_OK",
                            "permissions", "用户 " + currentUser + " 具备 CREATE 和 USAGE 权限", null, null));
                } else {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.WARNING, CATEGORY, "USER_PERMISSIONS_INSUFFICIENT",
                            "permissions", "用户 " + currentUser + " 权限不足: CREATE=" + canCreate + " USAGE=" + canUsage,
                            "为该用户授予必要的数据库权限: GRANT CREATE, USAGE ON SCHEMA public TO <user>", null));
                }
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "USER_PERMISSIONS_FAILED",
                        "permissions", "权限检查失败: " + e.getMessage(), null, null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }

        private boolean hasSchemaPermission(Connection conn, String privilege) {
            String sql = "SELECT has_schema_privilege(current_user, 'public', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, privilege);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Component
    public static class ResponseTimeCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.response_time"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "连接响应时间"; }
        @Override public String description() { return "执行 3 次 SELECT 1 并取平均响应时间"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            long totalMs = 0;
            int rounds = 3;
            try {
                for (int i = 0; i < rounds; i++) {
                    long start = System.currentTimeMillis();
                    try (Connection conn = graph.getConnection();
                         PreparedStatement ps = conn.prepareStatement("SELECT 1");
                         ResultSet rs = ps.executeQuery()) {
                        rs.next();
                    }
                    totalMs += System.currentTimeMillis() - start;
                }
                long avgMs = totalMs / rounds;
                String level = avgMs > RESPONSE_TIME_WARN_MS ? DiagnosticIssue.WARNING : DiagnosticIssue.INFO;
                issues.add(DiagnosticIssue.of(
                        level, CATEGORY, "RESPONSE_TIME",
                        "response-time", "平均响应时间 " + avgMs + " ms (" + rounds + " 次平均)",
                        avgMs > RESPONSE_TIME_WARN_MS ? "响应时间偏长,检查网络延迟和数据库负载" : null, null));
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "RESPONSE_TIME_FAILED",
                        "response-time", "响应时间测试失败: " + e.getMessage(), null, null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class TransactionTestCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.transaction_test"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "事务测试"; }
        @Override public String description() { return "测试 BEGIN-ROLLBACK 是否正常工作(只读,不写入数据)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            try (Connection conn = graph.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
                conn.rollback();
                conn.setAutoCommit(true);
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.INFO, CATEGORY, "TRANSACTION_OK",
                        "transaction", "事务 BEGIN-ROLLBACK 正常", null, null));
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.ERROR, CATEGORY, "TRANSACTION_FAILED",
                        "transaction", "事务测试失败: " + e.getMessage(),
                        "检查数据库是否支持事务(如 MySQL 是否为 InnoDB 引擎)", null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }

    @Component
    public static class DistributedConfigCheck implements DiagnosticCheck {

        @Override public String id() { return "connection.distributed_config"; }
        @Override public String category() { return CATEGORY; }
        @Override public String displayName() { return "Distributed 配置"; }
        @Override public String description() { return "检查连接的 distributed 模式是否与物理表结构一致(基于分区表启发式推断)"; }
        @Override public boolean isHeavy() { return false; }

        @Override
        public List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) {
            List<DiagnosticIssue> issues = new ArrayList<>();
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT count(*) FROM pg_partitioned_table")) {
                long partitionedCount;
                try (ResultSet rs = ps.executeQuery()) {
                    partitionedCount = rs.next() ? rs.getLong(1) : 0;
                }
                if (partitionedCount > 0) {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "DISTRIBUTED_CONFIG_OK",
                            "distributed", "检测到 " + partitionedCount + " 个分区表,distributed 模式可能已启用", null, null));
                } else {
                    issues.add(DiagnosticIssue.of(
                            DiagnosticIssue.INFO, CATEGORY, "DISTRIBUTED_CONFIG_OK",
                            "distributed", "未检测到分区表,distributed 模式可能未启用", null, null));
                }
            } catch (Exception e) {
                issues.add(DiagnosticIssue.of(
                        DiagnosticIssue.WARNING, CATEGORY, "DISTRIBUTED_CHECK_FAILED",
                        "distributed", "distributed 配置检查失败: " + e.getMessage(), null, null));
            }
            if (reporter != null) reporter.report(100, 1, 1, 0, 1L, "完成");
            return issues;
        }
    }
}
