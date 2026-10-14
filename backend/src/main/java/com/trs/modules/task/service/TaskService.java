package com.trs.modules.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.task.engine.TaskEngine;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.mapper.TaskMapper;
import com.trs.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 异步任务服务,负责任务 CRUD、状态流转、重试和任务编号生成。
 *
 * @author czh
 * @date 2026/07/23
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, String> TYPE_PREFIX = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_CATEGORY = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_DEFAULT_NAME = new LinkedHashMap<>();

    static {
        TYPE_PREFIX.put("GRAPH_ALGORITHM", "ALG");
        TYPE_CATEGORY.put("GRAPH_ALGORITHM", Task.CAT_STATISTICS);
        TYPE_DEFAULT_NAME.put("GRAPH_ALGORITHM", "图算法");
        TYPE_PREFIX.put("IMPORT_VERTEX", "IMP");
        TYPE_PREFIX.put("IMPORT_EDGE", "IMP");
        TYPE_PREFIX.put("IMPORT_TOPOLOGY", "IMP");
        TYPE_PREFIX.put("EXPORT_VERTEX", "EXP");
        TYPE_PREFIX.put("EXPORT_EDGE", "EXP");
        TYPE_PREFIX.put("EXPORT_TOPOLOGY", "EXP");
        TYPE_PREFIX.put("BATCH_DELETE_VERTEX", "DEL");
        TYPE_PREFIX.put("BATCH_DELETE_EDGE", "DEL");
        TYPE_PREFIX.put("CLEAR_VERTEX", "CLR");
        TYPE_PREFIX.put("CLEAR_EDGE", "CLR");
        TYPE_PREFIX.put("STAT_VERTEX_COUNTS", "STAT");
        TYPE_PREFIX.put("STAT_EDGE_COUNTS", "STAT");
        TYPE_PREFIX.put("STAT_ORPHAN", "STAT");
        TYPE_PREFIX.put("DIAG_TOPOLOGY", "DIA");
        TYPE_PREFIX.put("DIAG_INDEX", "DIA");
        TYPE_PREFIX.put("DIAG_DATA_INTEGRITY", "DIA");
        TYPE_PREFIX.put("DIAG_ORPHAN", "DIA");
        TYPE_PREFIX.put("DIAG_CUSTOM", "DIA");

        TYPE_CATEGORY.put("IMPORT_VERTEX", Task.CAT_IMPORT);
        TYPE_CATEGORY.put("IMPORT_EDGE", Task.CAT_IMPORT);
        TYPE_CATEGORY.put("IMPORT_TOPOLOGY", Task.CAT_IMPORT);
        TYPE_CATEGORY.put("EXPORT_VERTEX", Task.CAT_EXPORT);
        TYPE_CATEGORY.put("EXPORT_EDGE", Task.CAT_EXPORT);
        TYPE_CATEGORY.put("EXPORT_TOPOLOGY", Task.CAT_EXPORT);
        TYPE_CATEGORY.put("BATCH_DELETE_VERTEX", Task.CAT_MAINTENANCE);
        TYPE_CATEGORY.put("BATCH_DELETE_EDGE", Task.CAT_MAINTENANCE);
        TYPE_CATEGORY.put("CLEAR_VERTEX", Task.CAT_MAINTENANCE);
        TYPE_CATEGORY.put("CLEAR_EDGE", Task.CAT_MAINTENANCE);
        TYPE_CATEGORY.put("STAT_VERTEX_COUNTS", Task.CAT_STATISTICS);
        TYPE_CATEGORY.put("STAT_EDGE_COUNTS", Task.CAT_STATISTICS);
        TYPE_CATEGORY.put("STAT_ORPHAN", Task.CAT_STATISTICS);
        TYPE_CATEGORY.put("DIAG_TOPOLOGY", Task.CAT_DIAGNOSTIC);
        TYPE_CATEGORY.put("DIAG_INDEX", Task.CAT_DIAGNOSTIC);
        TYPE_CATEGORY.put("DIAG_DATA_INTEGRITY", Task.CAT_DIAGNOSTIC);
        TYPE_CATEGORY.put("DIAG_ORPHAN", Task.CAT_DIAGNOSTIC);
        TYPE_CATEGORY.put("DIAG_CUSTOM", Task.CAT_DIAGNOSTIC);

        TYPE_DEFAULT_NAME.put("IMPORT_VERTEX", "点数据导入");
        TYPE_DEFAULT_NAME.put("IMPORT_EDGE", "边数据导入");
        TYPE_DEFAULT_NAME.put("IMPORT_TOPOLOGY", "Topology 导入");
        TYPE_DEFAULT_NAME.put("EXPORT_VERTEX", "点数据导出");
        TYPE_DEFAULT_NAME.put("EXPORT_EDGE", "边数据导出");
        TYPE_DEFAULT_NAME.put("EXPORT_TOPOLOGY", "Topology 导出");
        TYPE_DEFAULT_NAME.put("BATCH_DELETE_VERTEX", "批量删除点");
        TYPE_DEFAULT_NAME.put("BATCH_DELETE_EDGE", "批量删除边");
        TYPE_DEFAULT_NAME.put("CLEAR_VERTEX", "清空点数据");
        TYPE_DEFAULT_NAME.put("CLEAR_EDGE", "清空边数据");
        TYPE_DEFAULT_NAME.put("STAT_VERTEX_COUNTS", "点数量统计");
        TYPE_DEFAULT_NAME.put("STAT_EDGE_COUNTS", "边数量统计");
        TYPE_DEFAULT_NAME.put("STAT_ORPHAN", "孤立点统计");
        TYPE_DEFAULT_NAME.put("DIAG_TOPOLOGY", "Topology 一致性检查");
        TYPE_DEFAULT_NAME.put("DIAG_INDEX", "索引检查");
        TYPE_DEFAULT_NAME.put("DIAG_DATA_INTEGRITY", "数据完整性检查");
        TYPE_DEFAULT_NAME.put("DIAG_ORPHAN", "孤立点检查");
        TYPE_DEFAULT_NAME.put("DIAG_CUSTOM", "自定义诊断任务");
    }

    private final TaskMapper taskMapper;
    private final GraphConnectionMapper connectionMapper;
    private final TaskEngine engine;
    private final com.trs.modules.graphAlgorithm.AlgorithmAccess algorithmAccess;

    private final String storageDir;

    /** 创建任务服务，图算法提交与重试均应用独立执行权限。 */
    public TaskService(TaskMapper taskMapper, GraphConnectionMapper connectionMapper,
                        TaskEngine engine, Environment env, com.trs.modules.graphAlgorithm.AlgorithmAccess algorithmAccess) {
        this.taskMapper = taskMapper;
        this.connectionMapper = connectionMapper;
        this.engine = engine;
        this.algorithmAccess = algorithmAccess;
        this.storageDir = env.getProperty("app.task.storage-dir", "./task-files");
    }

    public static Set<String> supportedTypes() {
        return TYPE_PREFIX.keySet();
    }

    public Task create(String taskType, Long connectionId, String schemaName,
                        String targetLabel, Map<String, Object> params, User user) {
        if (!TYPE_PREFIX.containsKey(taskType)) {
            throw new IllegalArgumentException("不支持的任务类型: " + taskType);
        }
        if ("GRAPH_ALGORITHM".equals(taskType)) {
            algorithmAccess.require(connectionId, "execute");
            if (params == null) throw new IllegalArgumentException("缺少算法参数");
            MAPPER.convertValue(params, com.trs.modules.graphAlgorithm.AlgorithmRequest.class);
        }

        Task task = new Task();
        task.setTaskType(taskType);
        task.setCategory(TYPE_CATEGORY.get(taskType));
        task.setTaskNo(generateTaskNo(taskType));
        task.setName(buildName(taskType, schemaName, targetLabel));
        task.setStatus(Task.STATUS_PENDING);
        task.setProgressPercent(0);
        task.setProcessedCount(0L);
        task.setSuccessCount(0L);
        task.setFailedCount(0L);
        task.setConnectionId(connectionId);
        task.setSchemaName(schemaName);
        task.setTargetLabel(targetLabel);
        task.setCreateTime(LocalDateTime.now());

        if (connectionId != null) {
            GraphConnection conn = connectionMapper.selectById(connectionId);
            if (conn != null) task.setConnectionName(conn.getName());
        }
        if (user != null) {
            task.setUserId(user.getId());
            task.setUsername(user.getUsername());
        }
        try {
            Map<String, Object> enrichedParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
            enrichedParams.put("subType", taskType);
            task.setParams(MAPPER.writeValueAsString(enrichedParams));
        } catch (Exception e) {
            task.setParams("{\"subType\":\"" + taskType + "\"}");
        }

        taskMapper.insert(task);
        engine.submit(task, storageDir);
        log.info("Created task: {} type={} connection={}", task.getTaskNo(), taskType, connectionId);
        return task;
    }

    public Task retry(Long taskId, User user) {
        Task original = taskMapper.selectById(taskId);
        if (original == null) throw new IllegalArgumentException("任务不存在: " + taskId);
        if ("GRAPH_ALGORITHM".equals(original.getTaskType()))
            algorithmAccess.require(original.getConnectionId(), "execute");

        Map<String, Object> params;
        try {
            params = original.getParams() == null || original.getParams().isBlank()
                    ? new LinkedHashMap<>() : MAPPER.readValue(original.getParams(), Map.class);
        } catch (Exception e) {
            params = new LinkedHashMap<>();
        }
        params.put("subType", original.getTaskType());

        Task retryTask = new Task();
        retryTask.setTaskType(original.getTaskType());
        retryTask.setCategory(original.getCategory());
        retryTask.setTaskNo(generateTaskNo(original.getTaskType()));
        retryTask.setName(original.getName() + " (重试)");
        retryTask.setStatus(Task.STATUS_PENDING);
        retryTask.setProgressPercent(0);
        retryTask.setProcessedCount(0L);
        retryTask.setSuccessCount(0L);
        retryTask.setFailedCount(0L);
        retryTask.setConnectionId(original.getConnectionId());
        retryTask.setConnectionName(original.getConnectionName());
        retryTask.setSchemaName(original.getSchemaName());
        retryTask.setTargetLabel(original.getTargetLabel());
        retryTask.setParams(original.getParams());
        retryTask.setParentTaskId(original.getId());
        retryTask.setCreateTime(LocalDateTime.now());

        if (user != null) {
            retryTask.setUserId(user.getId());
            retryTask.setUsername(user.getUsername());
        }

        taskMapper.insert(retryTask);
        engine.submit(retryTask, storageDir);
        log.info("Retried task {} as new task {}", original.getTaskNo(), retryTask.getTaskNo());
        return retryTask;
    }

    public boolean cancel(Long taskId) {
        return engine.cancel(taskId);
    }

    public boolean delete(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) return false;
        if (Task.STATUS_PENDING.equals(task.getStatus()) || Task.STATUS_RUNNING.equals(task.getStatus())) {
            throw new IllegalStateException("运行中或等待中的任务不能删除,请先取消");
        }

        if (task.getTaskNo() != null && !task.getTaskNo().isBlank()) {
            Path taskDir = Paths.get(storageDir, task.getTaskNo());
            if (Files.exists(taskDir)) {
                try (var stream = Files.walk(taskDir)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                } catch (Exception e) {
                    log.warn("Failed to delete task files for {}: {}", task.getTaskNo(), e.getMessage());
                }
            }
        }

        taskMapper.deleteById(taskId);
        return true;
    }

    public Task getById(Long id) { return taskMapper.selectById(id); }

    public Map<String, Object> page(int page, int size, String status, String category, String taskType,
                                      Long connectionId, Long userId, String schemaName, String targetLabel,
                                      String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        if (size > 200) size = 200;
        int offset = (page - 1) * size;
        List<Task> rows = taskMapper.selectPage(offset, size, status, category, taskType,
                connectionId, userId, schemaName, targetLabel, keyword, startTime, endTime);
        long total = taskMapper.selectCount(status, category, taskType, connectionId,
                userId, schemaName, targetLabel, keyword, startTime, endTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    private String generateTaskNo(String taskType) {
        String prefix = TYPE_PREFIX.get(taskType);
        String dateStr = LocalDate.now().format(DATE_FMT);
        long seq = taskMapper.countByDateAndPrefix(dateStr, prefix, taskType) + 1;
        return String.format("%s-%s-%03d", prefix, dateStr, seq);
    }

    private String buildName(String taskType, String schemaName, String targetLabel) {
        String base = TYPE_DEFAULT_NAME.getOrDefault(taskType, taskType);
        if (schemaName != null && targetLabel != null) {
            return base + ": " + schemaName + "." + targetLabel;
        }
        return base;
    }

    public String getStorageDir() { return storageDir; }
}
