package com.trs.modules.vertexData;

import com.trs.common.Result;
import com.trs.modules.vertexData.dto.VertexDetailDto;
import com.trs.modules.vertexData.dto.VertexSaveRequest;
import com.trs.modules.vertexData.service.VertexDataService;
import com.trs.security.PermissionChecker;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点数据管理 REST 控制器,提供 Vertex 实例数据的分页查询、详情、新增、编辑、删除和清空。
 *
 * @author czh
 * @date 2026/07/08
 */
@RestController
@RequestMapping("/vertex-data")
public class VertexDataController {

    private final VertexDataService service;
    private final UserPreferenceService preferenceService;
    private final PermissionChecker permissionChecker;

    public VertexDataController(VertexDataService service, UserPreferenceService preferenceService,
                                 PermissionChecker permissionChecker) {
        this.service = service;
        this.preferenceService = preferenceService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        List<Map<String, Object>> conns = service.listConnections();
        Long remembered = null;
        User u = currentUser();
        if (u != null) {
            remembered = preferenceService.getAsLong(
                    u.getId(), UserPreferenceService.KEY_VERTEX_DATA_ACTIVE_CONNECTION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", conns);
        result.put("activeConnectionId", remembered);
        return Result.ok(result);
    }

    @PutMapping("/active-connection")
    public Result<?> setActiveConnection(@RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        Object v = body.get("connectionId");
        if (v == null || "".equals(v)) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_VERTEX_DATA_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_VERTEX_DATA_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_VERTEX_DATA_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    @GetMapping("/{connectionId}/tree")
    public Result<Map<String, Object>> tree(@PathVariable Long connectionId) {
        return Result.ok(service.getTree(connectionId));
    }

    @PostMapping("/{connectionId}/refresh")
    public Result<?> refresh(@PathVariable Long connectionId) {
        service.evict(connectionId);
        return Result.ok();
    }

    @GetMapping("/{connectionId}/{schema}/{label}/properties")
    public Result<List<Map<String, Object>>> labelProperties(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.getVertexLabelProperties(connectionId, schema, label));
    }

    @GetMapping("/{connectionId}/{schema}/{label}")
    public Result<Map<String, Object>> page(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Map<String, String> allParams) {
        Map<String, Object> filters = new LinkedHashMap<>();
        for (var entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("page") && !key.equals("size") && !entry.getValue().isEmpty()) {
                filters.put(key, entry.getValue());
            }
        }
        return Result.ok(service.page(connectionId, schema, label, page, size, filters));
    }

    @GetMapping("/{connectionId}/vertex/{vertexId}")
    public Result<VertexDetailDto> detail(
            @PathVariable Long connectionId,
            @PathVariable String vertexId) {
        return Result.ok(service.getDetail(connectionId, vertexId));
    }

    @PostMapping("/{connectionId}")
    public Result<?> create(
            @PathVariable Long connectionId,
            @RequestBody VertexSaveRequest req) {
        permissionChecker.require("vertex_data:create");
        service.create(connectionId, req);
        return Result.ok();
    }

    @PutMapping("/{connectionId}/vertex/{vertexId}")
    public Result<?> update(
            @PathVariable Long connectionId,
            @PathVariable String vertexId,
            @RequestBody VertexSaveRequest req) {
        permissionChecker.require("vertex_data:update");
        service.update(connectionId, vertexId, req);
        return Result.ok();
    }

    @DeleteMapping("/{connectionId}/vertex/{vertexId}")
    public Result<?> delete(
            @PathVariable Long connectionId,
            @PathVariable String vertexId) {
        permissionChecker.require("vertex_data:delete");
        service.delete(connectionId, vertexId);
        return Result.ok();
    }

    @PostMapping("/{connectionId}/batch-delete")
    public Result<Map<String, Object>> batchDelete(
            @PathVariable Long connectionId,
            @RequestBody Map<String, Object> body) {
        permissionChecker.require("vertex_data:batch_delete");
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        int count = service.batchDelete(connectionId, ids);
        return Result.ok(Map.of("deleted", count));
    }

    @PostMapping("/{connectionId}/{schema}/{label}/clear")
    public Result<Map<String, Object>> clear(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label) {
        permissionChecker.require("vertex_data:clear");
        long count = service.clearVertices(connectionId, schema, label);
        return Result.ok(Map.of("deleted", count));
    }

    @GetMapping("/{connectionId}/{schema}/{label}/export")
    public Result<Map<String, Object>> export(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) Map<String, String> allParams) {
        Map<String, Object> filters = new LinkedHashMap<>();
        for (var entry : allParams.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("format") && !entry.getValue().isEmpty()) {
                filters.put(key, entry.getValue());
            }
        }
        return Result.ok(service.exportVertices(connectionId, schema, label, filters, format));
    }

    @GetMapping("/gremlin-examples/{schema}/{label}")
    public Result<List<Map<String, String>>> gremlinExamples(
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.generateGremlinExamples(schema, label));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
