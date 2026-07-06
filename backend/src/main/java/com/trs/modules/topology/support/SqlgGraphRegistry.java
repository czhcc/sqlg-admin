package com.trs.modules.topology.support;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SqlgGraphRegistry {

    private static final Logger log = LoggerFactory.getLogger(SqlgGraphRegistry.class);

    private final GraphConnectionMapper connectionMapper;
    private final Map<Long, SqlgGraph> cache = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastAccess = new ConcurrentHashMap<>();

    public SqlgGraphRegistry(GraphConnectionMapper connectionMapper) {
        this.connectionMapper = connectionMapper;
    }

    public SqlgGraph get(Long connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("connectionId 不能为空");
        }
        lastAccess.put(connectionId, System.currentTimeMillis());
        return cache.computeIfAbsent(connectionId, this::openGraph);
    }

    public void evict(Long connectionId) {
        SqlgGraph g = cache.remove(connectionId);
        lastAccess.remove(connectionId);
        if (g != null) {
            try {
                g.close();
                log.info("SqlgGraph for connection {} closed", connectionId);
            } catch (Exception e) {
                log.warn("Failed to close SqlgGraph for connection {}: {}", connectionId, e.getMessage());
            }
        }
    }

    public void evictAll() {
        cache.keySet().forEach(this::evict);
    }

    public boolean isOpen(Long connectionId) {
        SqlgGraph g = cache.get(connectionId);
        return g != null;
    }

    private SqlgGraph openGraph(Long connectionId) {
        GraphConnection c = connectionMapper.selectById(connectionId);
        if (c == null) {
            throw new IllegalArgumentException("连接不存在: id=" + connectionId);
        }
        if (c.getStatus() == null || c.getStatus() != 1) {
            throw new IllegalArgumentException("连接已停用: " + c.getName());
        }
        return openGraph(c);
    }

    private SqlgGraph openGraph(GraphConnection c) {
        try {
            Configuration config = new BaseConfiguration();
            config.setProperty("jdbc.url", c.getJdbcUrl());
            config.setProperty("jdbc.username", c.getUsername());
            config.setProperty("jdbc.password", c.getPassword());
            if (Boolean.TRUE.equals(c.getDistributed())) {
                config.setProperty("distributed", "true");
            }
            applyPoolConfig(config, c.getPoolConfig());

            log.info("Opening SqlgGraph for connection [{}] at {}", c.getName(), c.getJdbcUrl());
            SqlgGraph graph = SqlgGraph.open(config);
            log.info("SqlgGraph opened for connection [{}]", c.getName());
            return graph;
        } catch (Exception e) {
            throw new IllegalStateException("打开 SqlgGraph 失败: " + e.getMessage(), e);
        }
    }

    private void applyPoolConfig(Configuration config, String poolConfigJson) {
        if (poolConfigJson == null || poolConfigJson.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(poolConfigJson);
            root.fields().forEachRemaining(e -> {
                com.fasterxml.jackson.databind.JsonNode v = e.getValue();
                if (v.isNumber())      config.setProperty("sqlg.pool." + e.getKey(), v.numberValue());
                else if (v.isBoolean())config.setProperty("sqlg.pool." + e.getKey(), v.booleanValue());
                else                   config.setProperty("sqlg.pool." + e.getKey(), v.asText());
            });
        } catch (Exception e) {
            log.warn("Invalid pool_config JSON for connection, ignored: {}", e.getMessage());
        }
    }
}
