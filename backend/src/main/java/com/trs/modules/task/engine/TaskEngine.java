package com.trs.modules.task.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.trs.modules.task.entity.Task;
import com.trs.modules.task.mapper.TaskMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 异步任务执行引擎,负责任务提交、取消、心跳监控。
 * <p>
 * 使用独立的 {@link java.util.concurrent.ThreadPoolExecutor} 线程池执行任务,
 * 维护 taskId → Future 映射用于取消。定时扫描心跳超时的 RUNNING 任务并标记为 FAILED。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class TaskEngine {

    private static final Logger log = LoggerFactory.getLogger(TaskEngine.class);
    private static final int STALE_HEARTBEAT_MINUTES = 5;

    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor;
    private final TaskMapper taskMapper;
    private final TaskHandlerRegistry handlerRegistry;
    private final Map<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final Map<Long, TaskContext> runningContexts = new ConcurrentHashMap<>();

    public TaskEngine(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor,
                       TaskMapper taskMapper,
                       TaskHandlerRegistry handlerRegistry) {
        this.executor = executor;
        this.taskMapper = taskMapper;
        this.handlerRegistry = handlerRegistry;
    }

    public void submit(Task task, String storageDir) {
        TaskHandler handler = handlerRegistry.getHandler(task.getTaskType());
        if (handler == null) {
            taskMapper.updateResult(task.getId(), Task.STATUS_FAILED, 0,
                    0L, 0L, null,
                    "不支持的任务类型: " + task.getTaskType(), null, null, LocalDateTime.now());
            return;
        }

        TaskContext ctx = new TaskContext(task.getId(), task.getTaskNo(),
                task.getConnectionId(), task.getParams(), storageDir);
        runningContexts.put(task.getId(), ctx);

        taskMapper.updateStatus(task.getId(), Task.STATUS_RUNNING, LocalDateTime.now(), null);

        Future<?> future = executor.submit(() -> runTask(task, handler, ctx));
        runningFutures.put(task.getId(), future);
    }

    private void runTask(Task task, TaskHandler handler, TaskContext ctx) {
        try {
            String result = handler.execute(ctx, new DbProgressReporter(task.getId(), taskMapper));

            Long successCount = ctx.isCancelRequested() ? null : extractLong(result, "successCount");
            Long failedCount = ctx.isCancelRequested() ? null : extractLong(result, "failedCount");
            String resultFile = extractString(result, "resultFilePath");
            String errorFile = extractString(result, "errorFilePath");

            String finalStatus;
            if (ctx.isCancelRequested()) {
                finalStatus = Task.STATUS_CANCELLED;
            } else if (failedCount != null && failedCount > 0 && successCount != null && successCount > 0) {
                finalStatus = Task.STATUS_PARTIAL;
            } else if (failedCount != null && failedCount > 0) {
                finalStatus = Task.STATUS_FAILED;
            } else {
                finalStatus = Task.STATUS_SUCCESS;
            }

            taskMapper.updateResult(task.getId(), finalStatus, 100,
                    successCount == null ? 0 : successCount,
                    failedCount == null ? 0 : failedCount,
                    result, null, resultFile, errorFile, LocalDateTime.now());
            log.info("Task {} finished: {}", task.getTaskNo(), finalStatus);
        } catch (TaskContext.TaskCancelledException e) {
            taskMapper.updateResult(task.getId(), Task.STATUS_CANCELLED, 100,
                    0L, 0L, null, "用户取消", null, null, LocalDateTime.now());
            log.info("Task {} cancelled", task.getTaskNo());
        } catch (Exception e) {
            log.error("Task {} failed", task.getTaskNo(), e);
            taskMapper.updateResult(task.getId(), Task.STATUS_FAILED, 100,
                    0L, 0L, null, truncate(e.getMessage(), 2000), null, null, LocalDateTime.now());
        } finally {
            runningFutures.remove(task.getId());
            runningContexts.remove(task.getId());
        }
    }

    public boolean cancel(Long taskId) {
        TaskContext ctx = runningContexts.get(taskId);
        Task task = taskMapper.selectById(taskId);
        if (task == null) return false;

        if (Task.STATUS_PENDING.equals(task.getStatus())) {
            taskMapper.updateResult(taskId, Task.STATUS_CANCELLED, 0,
                    0L, 0L, null, "等待中取消", null, null, LocalDateTime.now());
            return true;
        }

        if (Task.STATUS_RUNNING.equals(task.getStatus()) && ctx != null) {
            ctx.requestCancel();
            return true;
        }

        return false;
    }

    @Scheduled(fixedDelay = 60000)
    public void scanStaleTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_HEARTBEAT_MINUTES);
        List<Task> stale = taskMapper.selectStaleRunning(threshold);
        for (Task t : stale) {
            log.warn("Task {} heartbeat timeout (last: {}), marking FAILED",
                    t.getTaskNo(), t.getLastHeartbeatTime());
            taskMapper.updateResult(t.getId(), Task.STATUS_FAILED, t.getProgressPercent(),
                    t.getSuccessCount(), t.getFailedCount(), t.getResultSummary(),
                    "心跳超时,疑似中断", t.getResultFilePath(), t.getErrorFilePath(),
                    LocalDateTime.now());
            Future<?> f = runningFutures.remove(t.getId());
            if (f != null) f.cancel(true);
            runningContexts.remove(t.getId());
        }
    }

    private Long extractLong(String json, String field) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asLong();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractString(String json, String field) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private record DbProgressReporter(Long taskId, TaskMapper mapper) implements ProgressReporter {
        @Override
        public void report(int progressPercent, long processedCount, long successCount,
                            long failedCount, Long totalCount, String currentStage) {
            mapper.updateProgress(taskId, progressPercent, processedCount,
                    successCount, failedCount, totalCount, currentStage, LocalDateTime.now());
        }
    }
}
