package com.trs.modules.connection.service;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GraphConnectionService.class);

    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private static final Map<String, String> DRIVER_MAP = new HashMap<>();

    static {
        DRIVER_MAP.put("POSTGRES", "org.postgresql.Driver");
        DRIVER_MAP.put("H2",        "org.h2.Driver");
        DRIVER_MAP.put("HSQLDB",    "org.hsqldb.jdbc.JDBCDriver");
        DRIVER_MAP.put("MARIADB",   "org.mariadb.jdbc.Driver");
        DRIVER_MAP.put("MYSQL",     "com.mysql.cj.jdbc.Driver");
    }

    private final GraphConnectionMapper mapper;
    private final OperationLogService logService;

    public GraphConnectionService(GraphConnectionMapper mapper, OperationLogService logService) {
        this.mapper = mapper;
        this.logService = logService;
    }

    public List<GraphConnection> list(String keyword) {
        return mapper.selectAll(keyword);
    }

    public GraphConnection getById(Long id) {
        return mapper.selectById(id);
    }

    @Transactional
    public GraphConnection create(GraphConnection c) {
        normalize(c);
        GraphConnection existing = mapper.selectByName(c.getName());
        if (existing != null) {
            throw new IllegalArgumentException("连接名称已存在: " + c.getName());
        }
        if (c.getStatus() == null) c.setStatus((short) 1);
        if (c.getIsDefault() == null) c.setIsDefault(false);
        mapper.insert(c);
        if (Boolean.TRUE.equals(c.getIsDefault())) {
            mapper.clearAllDefault();
            mapper.setDefault(c.getId());
        }
        GraphConnection result = mapper.selectById(c.getId());
        try {
            logService.log().module("连接管理").action("新增连接").httpMethod("POST")
                .type("CREATE").name("新增连接: " + c.getName())
                .status("SUCCESS").connection(c.getId()).objectType("Connection").objectName(c.getName())
                .objectName(c.getName()).result("成功").submit();
        } catch (Exception ignored) {}
        return result;
    }

    @Transactional
    public GraphConnection update(Long id, GraphConnection c) {
        GraphConnection exist = mapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("连接不存在: id=" + id);
        }
        normalize(c);
        c.setId(id);
        if (c.getStatus() == null) c.setStatus(exist.getStatus());
        if (c.getIsDefault() == null) c.setIsDefault(exist.getIsDefault());

        boolean passwordEmpty = c.getPassword() == null || c.getPassword().isEmpty();
        if (passwordEmpty) {
            mapper.updateWithoutPassword(c);
        } else {
            mapper.update(c);
        }

        if (Boolean.TRUE.equals(c.getIsDefault())) {
            mapper.clearAllDefault();
            mapper.setDefault(id);
        }
        try {
            logService.log().module("连接管理").action("编辑连接").httpMethod("PUT")
                .type("UPDATE").name("编辑连接: " + exist.getName())
                .status("SUCCESS").connection(id).objectType("Connection").objectName(exist.getName())
                .result("成功").submit();
        } catch (Exception ignored) {}
        return mapper.selectById(id);
    }

    @Transactional
    public void delete(Long id) {
        GraphConnection exist = mapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("连接不存在: id=" + id);
        }
        mapper.deleteById(id);
        try {
            logService.log().module("连接管理").action("删除连接").httpMethod("DELETE")
                .type("DELETE").name("删除连接: " + exist.getName()).dangerous()
                .status("SUCCESS").connection(id).objectType("Connection").objectName(exist.getName())
                .result("已删除").submit();
        } catch (Exception ignored) {}
    }

    @Transactional
    public void updateStatus(Long id, short status) {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status 只能是 0 或 1");
        }
        mapper.updateStatus(id, status);
        try {
            GraphConnection conn = mapper.selectById(id);
            logService.log().module("连接管理").action("更新状态").httpMethod("PUT")
                .type("UPDATE").name((status == 1 ? "启用" : "停用") + "连接: " + (conn != null ? conn.getName() : id))
                .status("SUCCESS").connection(id).objectType("Connection")
                .result(status == 1 ? "已启用" : "已停用").submit();
        } catch (Exception ignored) {}
    }

    @Transactional
    public void setDefault(Long id) {
        GraphConnection exist = mapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("连接不存在: id=" + id);
        }
        mapper.clearAllDefault();
        mapper.setDefault(id);
        try {
            logService.log().module("连接管理").action("设为默认连接").httpMethod("PUT")
                .type("UPDATE").name("设为默认连接: " + exist.getName())
                .status("SUCCESS").connection(id).objectType("Connection").objectName(exist.getName())
                .result("已设为默认").submit();
        } catch (Exception ignored) {}
    }

    public TestResult test(GraphConnection c) {
        if (c.getDbType() == null || c.getJdbcUrl() == null || c.getUsername() == null) {
            return TestResult.fail("dbType / jdbcUrl / username 不能为空");
        }
        if (c.getId() != null && (c.getPassword() == null || c.getPassword().isEmpty())) {
            GraphConnection saved = mapper.selectById(c.getId());
            if (saved != null) c.setPassword(saved.getPassword());
        }
        if (c.getPassword() == null) c.setPassword("");

        String driver = DRIVER_MAP.get(c.getDbType().toUpperCase());
        if (driver == null) {
            return TestResult.fail("不支持的数据库类型: " + c.getDbType()
                    + ",支持: " + DRIVER_MAP.keySet());
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            return TestResult.fail("JDBC 驱动未找到: " + driver);
        }
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try (Connection conn = DriverManager.getConnection(
                c.getJdbcUrl(), c.getUsername(), c.getPassword())) {
            String product = conn.getMetaData().getDatabaseProductName();
            String version = conn.getMetaData().getDatabaseProductVersion();
            return TestResult.ok(product + " " + version);
        } catch (SQLException e) {
            log.warn("Test connection failed for [{}]: {}", c.getName(), e.getMessage());
            return TestResult.fail(e.getMessage());
        }
    }

    private void normalize(GraphConnection c) {
        if (c.getDbType() != null) c.setDbType(c.getDbType().toUpperCase());
        if (c.getDistributed() == null) c.setDistributed(false);
    }

    public record TestResult(boolean success, String message) {
        public static TestResult ok(String message)  { return new TestResult(true, message); }
        public static TestResult fail(String message) { return new TestResult(false, message); }
    }
}
