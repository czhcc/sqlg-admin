package com.trs.modules.graphAlgorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.common.Result;
import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.service.TaskService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.security.RequirePermission;
import com.trs.user.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * 图算法目录、任务提交、结果读取与导出接口。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@RestController
@RequestMapping("/graph-algorithm")
public class GraphAlgorithmController {
    private final TaskService tasks;
    private final AlgorithmAccess access;
    private final ConnectionVisibilityHelper visibility;
    private final SqlgGraphRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 创建图算法控制器。 */
    public GraphAlgorithmController(TaskService tasks, AlgorithmAccess access,
            ConnectionVisibilityHelper visibility, SqlgGraphRegistry registry) {
        this.tasks = tasks; this.access = access; this.visibility = visibility; this.registry = registry;
    }

    /** 获取当前用户可见连接。 */
    @GetMapping("/connections")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:view", name = "查看图算法")
    public Result<?> connections() { return Result.ok(visibility.listConnectionDtosForCurrentUser()); }

    /** 获取所选连接的 Schema 与点边类型目录。 */
    @GetMapping("/{id}/scope")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:view", name = "查看图算法")
    public Result<?> scope(@PathVariable Long id) {
        access.require(id, "view");
        var graph = registry.get(id);
        try {
            return Result.ok(graph.getTopology().getSchemas().stream()
                    .sorted(Comparator.comparing(org.umlg.sqlg.structure.topology.Schema::getName))
                    .map(schema -> Map.of("name", schema.getName(),
                            "vertexLabels", schema.getVertexLabels().values().stream().map(label -> label.getName()).sorted().toList(),
                            "edgeLabels", schema.getEdgeLabels().values().stream().map(label -> label.getName()).sorted().toList())).toList());
        } finally { graph.tx().rollback(); }
    }

    /** 提交经权限和参数校验的图算法任务。 */
    @PostMapping("/{id}/tasks")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:execute", name = "执行图算法")
    public Result<?> create(@PathVariable Long id, @RequestBody AlgorithmRequest request) {
        User user = currentUser();
        return Result.ok(tasks.create("GRAPH_ALGORITHM", id, request.schema(), null,
                mapper.convertValue(request, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}), user));
    }

    /** 读取本人的算法任务状态，不在摘要中携带整图数据。 */
    @GetMapping("/tasks/{id}")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:view", name = "查看图算法")
    public Result<?> status(@PathVariable Long id) { return Result.ok(owned(id, "view")); }

    /** 读取已完成任务的独立 JSON 结果。 */
    @GetMapping("/tasks/{id}/result")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:view", name = "查看图算法")
    public Result<?> result(@PathVariable Long id) throws Exception {
        // Spring Boot 4 的 HTTP JSON 使用 Jackson 3，不返回 Jackson 2 的 JsonNode 对象。
        return Result.ok(mapper.readValue(readResult(owned(id, "view")),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
    }

    /** 导出完整算法结果，而非仅导出画布展示子集。 */
    @GetMapping("/tasks/{id}/export")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:export", name = "导出算法结果")
    public ResponseEntity<String> export(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=graph-algorithm-" + id + ".json")
                .body(readResult(owned(id, "export")));
    }

    /** 请求取消本人的算法任务。 */
    @PostMapping("/tasks/{id}/cancel")
    @RequirePermission(menu = "graph-algorithm", code = "graph_algorithm:execute", name = "执行图算法")
    public Result<?> cancel(@PathVariable Long id) {
        owned(id, "execute");
        tasks.cancel(id);
        return Result.ok();
    }

    private Task owned(Long id, String operation) {
        Task task = tasks.getById(id);
        User user = currentUser();
        if (task == null || !"GRAPH_ALGORITHM".equals(task.getTaskType()))
            throw new IllegalArgumentException("图算法任务不存在");
        if (!Objects.equals(task.getUserId(), user.getId()) && (user.getRoles() == null
                || Arrays.stream(user.getRoles().split(",")).map(String::trim).noneMatch("SUPER_ADMIN"::equals)))
            throw new SecurityException("无权访问其他用户的算法任务");
        access.require(task.getConnectionId(), operation);
        return task;
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user))
            throw new SecurityException("未登录");
        return user;
    }

    private String readResult(Task task) throws Exception {
        if (!Task.STATUS_SUCCESS.equals(task.getStatus()) || task.getResultFilePath() == null)
            throw new IllegalArgumentException("任务尚未成功完成");
        Path root = Path.of(tasks.getStorageDir()).toAbsolutePath().normalize();
        Path file = root.resolve(task.getResultFilePath()).normalize();
        if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath()))
            throw new SecurityException("无效的任务结果路径");
        return Files.readString(file);
    }
}
