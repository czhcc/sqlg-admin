package com.trs.modules.task;

import com.trs.common.Result;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.service.TaskService;
import com.trs.user.entity.User;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务中心 REST 控制器,提供任务列表、详情、创建、取消、重试、删除和文件下载接口。
 *
 * @author czh
 * @date 2026/07/23
 */
@RestController
@RequestMapping("/task")
public class TaskController {

    private final TaskService service;
    private final com.trs.modules.graphAlgorithm.AlgorithmAccess algorithmAccess;

    /** 创建任务控制器，并对图算法结果应用独立权限检查。 */
    public TaskController(TaskService service, com.trs.modules.graphAlgorithm.AlgorithmAccess algorithmAccess) {
        this.algorithmAccess = algorithmAccess;
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String schemaName,
            @RequestParam(required = false) String targetLabel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);

        Long effectiveUserId = null;
        boolean adminOverride = false;
        User current = currentUser();
        boolean isAdmin = current != null && isAdmin(current);

        if ("all".equalsIgnoreCase(userId)) {
            if (isAdmin) {
                adminOverride = true;
            } else {
                effectiveUserId = current != null ? current.getId() : null;
            }
        } else if (userId != null && !userId.isBlank()) {
            try {
                effectiveUserId = Long.valueOf(userId);
            } catch (NumberFormatException ignored) {
                effectiveUserId = current != null ? current.getId() : null;
            }
        } else {
            if (!isAdmin && current != null) {
                effectiveUserId = current.getId();
            }
        }

        // 非管理员始终限定本人，避免通过 userId 参数绕过任务访问隔离。
        if (!isAdmin && current != null) effectiveUserId = current.getId();
        return Result.ok(service.page(page, size, status, category, taskType, connectionId,
                adminOverride ? null : effectiveUserId,
                schemaName, targetLabel, keyword, start, end));
    }

    @GetMapping("/{id}")
    public Result<Task> detail(@PathVariable Long id) {
        Task task = service.getById(id);
        if (task == null) return Result.fail(404, "任务不存在");
        User current = currentUser();
        if (current != null && !isAdmin(current) && task.getUserId() != null
                && !task.getUserId().equals(current.getId())) {
            return Result.fail(403, "无权查看其他用户的任务");
        }
        if ("GRAPH_ALGORITHM".equals(task.getTaskType())) algorithmAccess.require(task.getConnectionId(), "view");
        return Result.ok(task);
    }

    @PostMapping
    public Result<Task> create(@RequestBody Map<String, Object> body) {
        String taskType = str(body, "taskType");
        Long connectionId = body.get("connectionId") instanceof Number n ? n.longValue() : null;
        String schemaName = str(body, "schemaName");
        String targetLabel = str(body, "targetLabel");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");

        User u = currentUser();
        return Result.ok(service.create(taskType, connectionId, schemaName, targetLabel, params, u));
    }

    @PostMapping("/{id}/retry")
    public Result<Task> retry(@PathVariable Long id) {
        checkOwnership(id);
        return Result.ok(service.retry(id, currentUser()));
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable Long id) {
        checkOwnership(id);
        Task task = service.getById(id);
        if (task != null && "GRAPH_ALGORITHM".equals(task.getTaskType())) algorithmAccess.require(task.getConnectionId(), "execute");
        boolean ok = service.cancel(id);
        if (!ok) return Result.fail(400, "任务无法取消(可能已结束)");
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        checkOwnership(id);
        try {
            service.delete(id);
            return Result.ok();
        } catch (IllegalStateException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                               @RequestParam(defaultValue = "result") String fileType) {
        checkOwnership(id);
        Task task = service.getById(id);
        if (task == null) return ResponseEntity.notFound().build();

        if ("GRAPH_ALGORITHM".equals(task.getTaskType())) algorithmAccess.require(task.getConnectionId(), "export");
        String relPath = "error".equals(fileType) ? task.getErrorFilePath() : task.getResultFilePath();
        if (relPath == null || relPath.isBlank()) return ResponseEntity.notFound().build();

        Path file = Paths.get(service.getStorageDir(), relPath);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        FileSystemResource resource = new FileSystemResource(file);
        String fileName = file.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.toFile().length())
                .body(resource);
    }

    @GetMapping("/types")
    public Result<Map<String, Object>> types() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, List<String>> byCategory = new LinkedHashMap<>();
        byCategory.put("IMPORT", List.of("IMPORT_VERTEX", "IMPORT_EDGE", "IMPORT_TOPOLOGY"));
        byCategory.put("EXPORT", List.of("EXPORT_VERTEX", "EXPORT_EDGE", "EXPORT_TOPOLOGY"));
        byCategory.put("MAINTENANCE", List.of("BATCH_DELETE_VERTEX", "BATCH_DELETE_EDGE", "CLEAR_VERTEX", "CLEAR_EDGE"));
        byCategory.put("STATISTICS", List.of("STAT_VERTEX_COUNTS", "STAT_EDGE_COUNTS", "STAT_ORPHAN", "GRAPH_ALGORITHM"));
        byCategory.put("DIAGNOSTIC", List.of("DIAG_TOPOLOGY", "DIAG_INDEX", "DIAG_DATA_INTEGRITY", "DIAG_ORPHAN", "DIAG_CUSTOM"));
        result.put("categories", byCategory);
        return Result.ok(result);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getPrincipal() instanceof User u ? u : null;
    }

    private boolean isAdmin(User u) {
        return u.getRoles() != null && java.util.Arrays.stream(u.getRoles().split(","))
                .map(String::trim).anyMatch("SUPER_ADMIN"::equals);
    }

    private void checkOwnership(Long taskId) {
        Task checkedTask = service.getById(taskId);
        if (checkedTask != null && "GRAPH_ALGORITHM".equals(checkedTask.getTaskType()))
            algorithmAccess.require(checkedTask.getConnectionId(), "view");
        User current = currentUser();
        if (current == null) return;
        if (isAdmin(current)) return;
        Task task = service.getById(taskId);
        if (task == null) return;
        if (task.getUserId() != null && !task.getUserId().equals(current.getId())) {
            throw new SecurityException("无权操作其他用户的任务");
        }
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
