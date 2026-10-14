package com.trs.modules.graphAlgorithm;

import com.trs.modules.task.engine.*;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.mapper.TaskMapper;
import com.trs.modules.task.handlers.AbstractTaskHandler;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.log.service.OperationLogService;
import com.trs.user.mapper.UserMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/**
 * 四种图算法的异步任务处理器，先释放读事务再执行内存计算。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@Component
public class GraphAlgorithmTaskHandler extends AbstractTaskHandler {
    private final GraphProjectionService projection;
    private final AlgorithmRunner runner;
    private final AlgorithmAccess access;
    private final TaskMapper tasks;
    private final UserMapper users;
    private final OperationLogService logs;

    /** 创建图算法任务处理器。 */
    public GraphAlgorithmTaskHandler(SqlgGraphRegistry registry, GraphProjectionService projection,
            AlgorithmRunner runner, AlgorithmAccess access, TaskMapper tasks, UserMapper users, OperationLogService logs) {
        super(registry);
        this.projection = projection; this.runner = runner; this.access = access;
        this.tasks = tasks; this.users = users; this.logs = logs;
    }

    /** 返回任务类型。 */
    @Override public String taskType() { return "GRAPH_ALGORITHM"; }

    /** 返回任务显示名称。 */
    @Override public String buildDisplayName(Task task) { return task.getName(); }

    /** 在独立任务线程重新加载提交者身份并执行算法。 */
    @Override public String execute(TaskContext ctx, ProgressReporter reporter) throws Exception {
        var previous = SecurityContextHolder.getContext();
        long started = System.nanoTime();
        try {
            Task task = tasks.selectById(ctx.getTaskId());
            var user = task == null || task.getUserId() == null ? null : users.selectById(task.getUserId());
            if (user == null || user.getStatus() == null || user.getStatus() != 1)
                throw new SecurityException("任务提交用户不存在或已停用");
            var security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
            SecurityContextHolder.setContext(security);
            access.require(ctx.getConnectionId(), "execute");
            var request = MAPPER.readValue(ctx.getParams(), AlgorithmRequest.class);
            long deadline = started + request.timeoutSeconds() * 1_000_000_000L;
            Runnable check = () -> {
                ctx.throwIfCancelled();
                if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
                    throw new IllegalStateException("图算法执行超时或被中断");
            };
            report(reporter, 5, 0, 0, 0, null, "读取子图");
            Instant readStarted = Instant.now();
            AlgorithmGraph data = projection.read(ctx.getConnectionId(), request, check);
            Instant readEnded = Instant.now();
            report(reporter, 50, data.nodes.size(), 0, 0, null, "内存计算");
            Map<String, Object> result = runner.run(data, request, check);
            result.put("algorithm", request.algorithm());
            result.put("parameters", request);
            result.put("vertexCount", data.nodes.size());
            result.put("edgeCount", data.edges.size());
            result.put("readStarted", readStarted.toString());
            result.put("readEnded", readEnded.toString());
            result.put("snapshot", false);
            result.put("costMs", (System.nanoTime() - started) / 1_000_000);
            result.put("nodes", data.nodes.values());
            result.put("edges", data.edges.values());
            check.run();
            String file = writeResultFile(ctx, "graph_algorithm.json", MAPPER.writeValueAsString(result));
            report(reporter, 100, data.nodes.size(), 1, 0, 1L, "完成");
            audit(ctx, "SUCCESS", "algorithm=" + request.algorithm() + ", vertices=" + data.nodes.size()
                    + ", edges=" + data.edges.size(), started);
            return MAPPER.writeValueAsString(Map.of("successCount", 1, "failedCount", 0,
                    "resultFilePath", file, "algorithm", request.algorithm(), "vertexCount", data.nodes.size(),
                    "edgeCount", data.edges.size()));
        } catch (Exception e) {
            audit(ctx, ctx.isCancelRequested() ? "BLOCKED" : "FAILED", e.getMessage(), started);
            throw e;
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void audit(TaskContext ctx, String status, String detail, long started) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var user = authentication != null && authentication.getPrincipal() instanceof com.trs.user.entity.User u ? u : null;
        logs.log().module("图算法").action("执行算法").type("QUERY").httpMethod("POST")
                .user(user == null ? null : user.getId(), user == null ? null : user.getUsername())
                .params(ctx.getParams()).costMs((int) ((System.nanoTime() - started) / 1_000_000))
                .name("图算法: " + ctx.getTaskNo()).connection(ctx.getConnectionId())
                .status(status).result(detail).submit();
    }
}
