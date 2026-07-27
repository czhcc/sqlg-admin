package com.trs.modules.task.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import com.trs.modules.task.engine.TaskHandler;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.umlg.sqlg.structure.SqlgGraph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 任务处理器基类,提供参数解析、进度上报、文件写入等公共能力。
 *
 * @author czh
 * @date 2026/07/23
 */
public abstract class AbstractTaskHandler implements TaskHandler {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final SqlgGraphRegistry registry;

    protected AbstractTaskHandler(SqlgGraphRegistry registry) {
        this.registry = registry;
    }

    protected SqlgGraph graph(TaskContext ctx) {
        SqlgGraph g = registry.get(ctx.getConnectionId());
        try { g.tx().rollback(); } catch (Exception ignored) {}
        return g;
    }

    protected JsonNode parseParams(TaskContext ctx) {
        if (ctx.getParams() == null || ctx.getParams().isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(ctx.getParams());
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    protected String str(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    protected String str(JsonNode node, String field, String def) {
        String v = str(node, field);
        return v == null ? def : v;
    }

    protected ObjectNode result() {
        return MAPPER.createObjectNode();
    }

    protected String writeResultFile(TaskContext ctx, String fileName, String content) throws Exception {
        Path dir = Paths.get(ctx.getStorageDir(), ctx.getTaskNo());
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return ctx.getTaskNo() + "/" + fileName;
    }

    protected void report(ProgressReporter reporter, int percent, long processed, long success,
                            long failed, Long total, String stage) {
        if (reporter != null) {
            reporter.report(percent, processed, success, failed, total, stage);
        }
    }
}
