package com.trs.modules.workspace.service;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.entity.OperationLog;
import com.trs.modules.log.mapper.OperationLogMapper;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.workspace.dto.AlertItemDto;
import com.trs.modules.workspace.dto.ConnectionStatusDto;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto.ConnectionInfo;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto.DataStats;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto.LabelCountItem;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto.TopologyStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作台服务,聚合当前连接的 Topology 统计、数据规模、连接状态和异常提醒。
 *
 * @author czh
 * @date 2026/07/23
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int TOP_N_LABELS = 10;
    private static final long LARGE_LABEL_THRESHOLD = 10_000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> DRIVER_MAP = new HashMap<>();

    static {
        DRIVER_MAP.put("POSTGRES", "org.postgresql.Driver");
        DRIVER_MAP.put("H2", "org.h2.Driver");
        DRIVER_MAP.put("HSQLDB", "org.hsqldb.jdbc.JDBCDriver");
        DRIVER_MAP.put("MARIADB", "org.mariadb.jdbc.Driver");
        DRIVER_MAP.put("MYSQL", "com.mysql.cj.jdbc.Driver");
    }

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final ConnectionVisibilityHelper visibilityHelper;
    private final OperationLogMapper operationLogMapper;

    public WorkspaceService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                            ConnectionVisibilityHelper visibilityHelper,
                            OperationLogMapper operationLogMapper) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.visibilityHelper = visibilityHelper;
        this.operationLogMapper = operationLogMapper;
    }

    public List<Map<String, Object>> listConnections() {
        return visibilityHelper.listConnectionDtosForCurrentUser();
    }

    public WorkspaceOverviewDto getOverview(Long connectionId) {
        GraphConnection conn = connectionMapper.selectById(connectionId);
        if (conn == null) {
            throw new IllegalArgumentException("连接不存在: id=" + connectionId);
        }

        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        ConnectionInfo connInfo = buildConnectionInfo(conn, graph);
        TopologyStats topologyStats = new TopologyStats();
        DataStats dataStats = new DataStats();

        List<LabelCountItem> vertexCounts = new ArrayList<>();
        List<LabelCountItem> edgeCounts = new ArrayList<>();

        long totalVertices = 0;
        long totalEdges = 0;
        int emptyVertexLabels = 0;
        int emptyEdgeLabels = 0;

        List<Schema> schemas = sortedSchemas(topology);
        topologyStats.setSchemaCount(schemas.size());

        for (Schema schema : schemas) {
            for (VertexLabel vl : sortedVertexLabels(schema)) {
                topologyStats.setVertexLabelCount(topologyStats.getVertexLabelCount() + 1);
                topologyStats.setVertexPropertyCount(
                        topologyStats.getVertexPropertyCount() + vl.getProperties().size());
                topologyStats.setIndexCount(
                        topologyStats.getIndexCount() + vl.getIndexes().size());
                topologyStats.setIdentifierCount(
                        topologyStats.getIdentifierCount() + extractIdentifierCount(vl));

                long count = countRows(graph, schema.getName(), "V_" + vl.getName());
                totalVertices += count;
                vertexCounts.add(new LabelCountItem(schema.getName(), vl.getName(), count));
                if (count == 0) emptyVertexLabels++;
            }

            for (EdgeLabel el : sortedEdgeLabels(schema)) {
                topologyStats.setEdgeLabelCount(topologyStats.getEdgeLabelCount() + 1);
                topologyStats.setEdgePropertyCount(
                        topologyStats.getEdgePropertyCount() + el.getProperties().size());
                topologyStats.setIndexCount(
                        topologyStats.getIndexCount() + el.getIndexes().size());
                topologyStats.setIdentifierCount(
                        topologyStats.getIdentifierCount() + extractIdentifierCount(el));

                long count = countRows(graph, schema.getName(), "E_" + el.getName());
                totalEdges += count;
                edgeCounts.add(new LabelCountItem(schema.getName(), el.getName(), count));
                if (count == 0) emptyEdgeLabels++;
            }
        }

        topologyStats.setEmptyVertexLabelCount(emptyVertexLabels);
        topologyStats.setEmptyEdgeLabelCount(emptyEdgeLabels);

        dataStats.setTotalVertices(totalVertices);
        dataStats.setTotalEdges(totalEdges);
        dataStats.setEmptyVertexLabelCount(emptyVertexLabels);
        dataStats.setEmptyEdgeLabelCount(emptyEdgeLabels);

        vertexCounts.stream().max(Comparator.comparingLong(LabelCountItem::getCount))
                .ifPresent(item -> {
                    if (item.getCount() > 0) dataStats.setLargestVertexLabel(item);
                });
        edgeCounts.stream().max(Comparator.comparingLong(LabelCountItem::getCount))
                .ifPresent(item -> {
                    if (item.getCount() > 0) dataStats.setLargestEdgeLabel(item);
                });

        dataStats.setOrphanVertexCount(countOrphanVertices(graph));

        List<LabelCountItem> topV = vertexCounts.stream()
                .sorted(Comparator.comparingLong(LabelCountItem::getCount).reversed())
                .limit(TOP_N_LABELS)
                .collect(Collectors.toList());
        List<LabelCountItem> topE = edgeCounts.stream()
                .sorted(Comparator.comparingLong(LabelCountItem::getCount).reversed())
                .limit(TOP_N_LABELS)
                .collect(Collectors.toList());

        WorkspaceOverviewDto dto = new WorkspaceOverviewDto();
        dto.setConnection(connInfo);
        dto.setTopology(topologyStats);
        dto.setDataStats(dataStats);
        dto.setTopVertexLabels(topV);
        dto.setTopEdgeLabels(topE);
        return dto;
    }

    public List<ConnectionStatusDto> getConnectionStatus() {
        List<GraphConnection> connections = visibilityHelper.listEnabledForCurrentUser();
        String now = LocalDateTime.now().format(TIME_FMT);
        List<ConnectionStatusDto> result = new ArrayList<>();
        for (GraphConnection c : connections) {
            result.add(pingConnection(c, now));
        }
        return result;
    }

    public List<AlertItemDto> getAlerts(Long connectionId) {
        List<AlertItemDto> alerts = new ArrayList<>();
        String now = LocalDateTime.now().format(TIME_FMT);

        GraphConnection conn = connectionMapper.selectById(connectionId);
        if (conn == null) {
            throw new IllegalArgumentException("连接不存在: id=" + connectionId);
        }

        ConnectionStatusDto status = pingConnection(conn, now);
        if (!"ok".equals(status.getStatus())) {
            alerts.add(new AlertItemDto("error", "connection",
                    "连接异常",
                    conn.getName() + " 连接失败: " + status.getErrorMessage(),
                    "/connection"));
        }

        try {
            SqlgGraph graph = registry.get(connectionId);
            Topology topology = graph.getTopology();
            List<Schema> schemas = sortedSchemas(topology);

            for (Schema schema : schemas) {
                for (VertexLabel vl : sortedVertexLabels(schema)) {
                    String fullName = schema.getName() + "." + vl.getName();
                    long count = countRows(graph, schema.getName(), "V_" + vl.getName());

                    if (!tableExists(graph, schema.getName(), "V_" + vl.getName())) {
                        alerts.add(new AlertItemDto("error", "topology",
                                "表结构不一致",
                                fullName + " 在 Topology 中存在,但底层表不存在",
                                "/topology"));
                    } else if (count == 0) {
                        alerts.add(new AlertItemDto("warning", "data",
                                "空点类型",
                                fullName + " 暂无点数据",
                                "/vertex-data"));
                    } else if (count > LARGE_LABEL_THRESHOLD && vl.getIndexes().isEmpty()
                            && vl.getIdentifiers() == null) {
                        alerts.add(new AlertItemDto("warning", "topology",
                                "大数据量缺少索引",
                                fullName + " 有 " + count + " 条数据但无索引,可能影响查询性能",
                                "/vertex-type"));
                    }
                }

                for (EdgeLabel el : sortedEdgeLabels(schema)) {
                    String fullName = schema.getName() + "." + el.getName();
                    long count = countRows(graph, schema.getName(), "E_" + el.getName());

                    if (!tableExists(graph, schema.getName(), "E_" + el.getName())) {
                        alerts.add(new AlertItemDto("error", "topology",
                                "表结构不一致",
                                fullName + " 在 Topology 中存在,但底层表不存在",
                                "/topology"));
                    } else if (count == 0) {
                        alerts.add(new AlertItemDto("warning", "data",
                                "空边类型",
                                fullName + " 暂无边数据",
                                "/edge-data"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan alerts for connection {}: {}", connectionId, e.getMessage());
            alerts.add(new AlertItemDto("error", "connection",
                    "无法读取 Topology",
                    e.getMessage(), "/connection"));
        }

        try {
            List<OperationLog> recentDangerous = operationLogMapper.selectPage(
                    0, 5, null, null, "FAILED", connectionId,
                    null, true, null, null, null);
            for (OperationLog op : recentDangerous) {
                alerts.add(new AlertItemDto("warning", "operation",
                        "危险操作异常",
                        op.getUsername() + " 执行 " + op.getOperationName() + " 失败"
                                + (op.getErrorMessage() != null ? ": " + op.getErrorMessage() : ""),
                        "/operation-log"));
            }
        } catch (Exception e) {
            log.debug("Failed to query recent dangerous operations: {}", e.getMessage());
        }

        return alerts;
    }

    private ConnectionInfo buildConnectionInfo(GraphConnection conn, SqlgGraph graph) {
        ConnectionInfo info = new ConnectionInfo();
        info.setId(conn.getId());
        info.setName(conn.getName());
        info.setDbType(conn.getDbType());
        info.setJdbcUrl(conn.getJdbcUrl());
        info.setUsername(conn.getUsername());
        info.setEnvironment(deriveEnvironment(conn.getName()));
        info.setLastCheckTime(LocalDateTime.now().format(TIME_FMT));

        try {
            Topology topology = graph.getTopology();
            info.setSchemaCount(topology.getSchemas().size());
        } catch (Exception ignored) {
            info.setSchemaCount(0);
        }

        ConnectionStatusDto ping = pingConnection(conn, info.getLastCheckTime());
        info.setStatus(ping.getStatus());
        info.setStatusMessage(ping.getErrorMessage());
        info.setResponseTimeMs(ping.getResponseTimeMs() != null ? ping.getResponseTimeMs() : 0);

        return info;
    }

    private ConnectionStatusDto pingConnection(GraphConnection c, String checkTime) {
        if (c.getDbType() == null || c.getJdbcUrl() == null) {
            return ConnectionStatusDto.fail(c.getId(), c.getName(), c.getDbType(),
                    deriveEnvironment(c.getName()), "配置不完整", checkTime);
        }

        String driver = DRIVER_MAP.get(c.getDbType().toUpperCase());
        if (driver == null) {
            return ConnectionStatusDto.fail(c.getId(), c.getName(), c.getDbType(),
                    deriveEnvironment(c.getName()), "不支持的数据库类型: " + c.getDbType(), checkTime);
        }

        long start = System.currentTimeMillis();
        try {
            Class.forName(driver);
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            try (Connection jdbc = DriverManager.getConnection(
                    c.getJdbcUrl(), c.getUsername(), c.getPassword() == null ? "" : c.getPassword())) {
                long elapsed = System.currentTimeMillis() - start;
                String product = jdbc.getMetaData().getDatabaseProductName();
                return ConnectionStatusDto.ok(c.getId(), c.getName(), c.getDbType(),
                        deriveEnvironment(c.getName()), elapsed, checkTime);
            }
        } catch (Exception e) {
            return ConnectionStatusDto.fail(c.getId(), c.getName(), c.getDbType(),
                    deriveEnvironment(c.getName()), e.getMessage(), checkTime);
        }
    }

    private long countRows(SqlgGraph graph, String schemaName, String physicalTable) {
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM \"" + schemaName + "\".\"" + physicalTable + "\"")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    private boolean tableExists(SqlgGraph graph, String schemaName, String tableName) {
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.tables "
                             + "WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 统计孤立点(无任何边连接的点),通过 Gremlin 遍历实现,失败时返回 -1。
     */
    private long countOrphanVertices(SqlgGraph graph) {
        try {
            return graph.traversal().V()
                    .not(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.bothE())
                    .count().next();
        } catch (Exception e) {
            log.debug("Failed to count orphan vertices: {}", e.getMessage());
            return -1;
        }
    }

    private int extractIdentifierCount(AbstractLabel label) {
        try {
            var ids = label.getIdentifiers();
            return ids == null ? 0 : ids.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String deriveEnvironment(String name) {
        if (name == null) return "—";
        String lower = name.toLowerCase();
        if (lower.contains("prod")) return "生产";
        if (lower.contains("test") || lower.contains("staging") || lower.contains("qa")) return "测试";
        if (lower.contains("dev") || lower.contains("local")) return "开发";
        return "—";
    }

    private List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }

    private List<VertexLabel> sortedVertexLabels(Schema schema) {
        return schema.getVertexLabels().values().stream()
                .sorted(Comparator.comparing(AbstractLabel::getName))
                .collect(Collectors.toList());
    }

    private List<EdgeLabel> sortedEdgeLabels(Schema schema) {
        return schema.getEdgeLabels().values().stream()
                .sorted(Comparator.comparing(AbstractLabel::getName))
                .collect(Collectors.toList());
    }
}
