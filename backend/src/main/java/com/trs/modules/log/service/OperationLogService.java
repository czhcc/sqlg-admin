package com.trs.modules.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.entity.OperationLog;
import com.trs.modules.log.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志服务,提供日志写入和查询能力。
 *
 * @author czh
 * @date 2026/07/13
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 200;

    private final OperationLogMapper logMapper;
    private final GraphConnectionMapper connectionMapper;

    public OperationLogService(OperationLogMapper logMapper, GraphConnectionMapper connectionMapper) {
        this.logMapper = logMapper;
        this.connectionMapper = connectionMapper;
    }

    public Map<String, Object> page(int page, int size, String module, String operationType,
                                      String status, Long connectionId, String username,
                                      Boolean isDangerous, LocalDateTime startTime,
                                      LocalDateTime endTime, String keyword) {
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        int offset = (page - 1) * size;

        List<OperationLog> rows = logMapper.selectPage(offset, size, module, operationType, status,
                connectionId, username, isDangerous, startTime, endTime, keyword);
        long total = logMapper.selectCount(module, operationType, status, connectionId, username,
                isDangerous, startTime, endTime, keyword);

        return Map.of("total", total, "rows", rows, "page", page, "size", size);
    }

    public OperationLog getById(Long id) {
        return logMapper.selectById(id);
    }

    public List<String> listModules() {
        return logMapper.selectDistinctModules();
    }

    public void deleteBefore(LocalDateTime before) {
        logMapper.deleteBefore(before);
    }

    public void asyncLog(LogContext ctx) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(ctx.userId);
            entry.setUsername(ctx.username);
            entry.setModule(ctx.module);
            entry.setAction(ctx.action);
            entry.setMethod(ctx.httpMethod);
            entry.setOperationType(ctx.operationType);
            entry.setOperationName(ctx.operationName);
            entry.setStatus(ctx.status);
            entry.setConnectionId(ctx.connectionId);
            entry.setConnectionName(ctx.connectionName);
            entry.setJdbcUrlMasked(ctx.jdbcUrlMasked);
            entry.setSchemaName(ctx.schemaName);
            entry.setObjectType(ctx.objectType);
            entry.setObjectName(ctx.objectName);
            entry.setObjectId(ctx.objectId);
            entry.setDetail(ctx.detail);
            entry.setAffectedCount(ctx.affectedCount);
            entry.setErrorMessage(ctx.errorMessage);
            entry.setCostMs(ctx.costMs);
            entry.setIsDangerous(ctx.isDangerous);

            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                entry.setIp(getClientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            }

            entry.setParams(ctx.params);
            entry.setResult(ctx.result);

            logMapper.insert(entry);
        } catch (Exception e) {
            log.warn("Failed to write operation log: {}", e.getMessage());
        }
    }

    public LogContext.Builder log() {
        return new LogContext.Builder(this);
    }

    void fillConnectionInfo(LogContext ctx) {
        if (ctx.connectionId == null) return;
        try {
            GraphConnection conn = connectionMapper.selectById(ctx.connectionId);
            if (conn != null) {
                ctx.connectionName = conn.getName();
                ctx.jdbcUrlMasked = maskJdbcUrl(conn.getJdbcUrl());
            }
        } catch (Exception ignored) {}
    }

    private static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip;
    }

    static String maskJdbcUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]*", "password=***");
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static class LogContext {
        Long userId;
        String username;
        String module;
        String action;
        String httpMethod;
        String params;
        String result;
        String operationType;
        String operationName;
        String status = "SUCCESS";
        Long connectionId;
        String connectionName;
        String jdbcUrlMasked;
        String schemaName;
        String objectType;
        String objectName;
        String objectId;
        String detail;
        Integer affectedCount;
        String errorMessage;
        Integer costMs;
        Boolean isDangerous = false;

        private final OperationLogService service;

        LogContext(OperationLogService service) {
            this.service = service;
        }

        public static class Builder {
            private final LogContext ctx;
            private final OperationLogService service;

            Builder(OperationLogService service) {
                this.service = service;
                this.ctx = new LogContext(service);
            }

            public Builder user(Long userId, String username) { ctx.userId = userId; ctx.username = username; return this; }
            public Builder module(String module) { ctx.module = module; return this; }
            public Builder action(String action) { ctx.action = action; return this; }
            public Builder httpMethod(String m) { ctx.httpMethod = m; return this; }
            public Builder type(String type) { ctx.operationType = type; return this; }
            public Builder name(String name) { ctx.operationName = name; return this; }
            public Builder status(String status) { ctx.status = status; return this; }
            public Builder connection(Long connId) { ctx.connectionId = connId; return this; }
            public Builder schema(String schema) { ctx.schemaName = schema; return this; }
            public Builder objectType(String t) { ctx.objectType = t; return this; }
            public Builder objectName(String n) { ctx.objectName = n; return this; }
            public Builder objectId(String id) { ctx.objectId = id; return this; }
            public Builder detail(String d) { ctx.detail = d; return this; }
            public Builder affected(Integer c) { ctx.affectedCount = c; return this; }
            public Builder error(String e) { ctx.errorMessage = e; return this; }
            public Builder costMs(Integer c) { ctx.costMs = c; return this; }
            public Builder dangerous() { ctx.isDangerous = true; return this; }
            public Builder params(String p) { ctx.params = p; return this; }
            public Builder result(String r) { ctx.result = r; return this; }

            public void submit() {
                service.fillConnectionInfo(ctx);
                service.asyncLog(ctx);
            }
        }
    }
}
