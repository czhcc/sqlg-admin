package com.trs.modules.edgeData;

import com.trs.common.Result;
import com.trs.modules.edgeData.dto.EdgeDetailDto;
import com.trs.modules.edgeData.dto.EdgeSaveRequest;
import com.trs.modules.edgeData.service.EdgeDataService;
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
 * 边数据管理 REST 控制器,提供 Edge 实例数据的分页查询、详情、新增、编辑、删除和清空,
 * 支持按出点/入点过滤、顶点搜索及数据导出。
 *
 * @author czh
 * @date 2026/07/09
 */
@RestController
@RequestMapping("/edge-data")
public class EdgeDataController {

    private final EdgeDataService service;
    private final UserPreferenceService preferenceService;
    private final PermissionChecker permissionChecker;

    public EdgeDataController(EdgeDataService service, UserPreferenceService preferenceService,
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
                    u.getId(), UserPreferenceService.KEY_EDGE_DATA_ACTIVE_CONNECTION);
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
                    UserPreferenceService.KEY_EDGE_DATA_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_EDGE_DATA_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_EDGE_DATA_ACTIVE_CONNECTION,
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
        return Result.ok(service.getEdgeLabelProperties(connectionId, schema, label));
    }

    @GetMapping("/{connectionId}/{schema}/{label}/vertex-labels")
    public Result<Map<String, Object>> edgeVertexLabels(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.getEdgeVertexLabels(connectionId, schema, label));
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

    @GetMapping("/{connectionId}/edge/{edgeId}")
    public Result<EdgeDetailDto> detail(
            @PathVariable Long connectionId,
            @PathVariable String edgeId) {
        return Result.ok(service.getDetail(connectionId, edgeId));
    }

    @PostMapping("/{connectionId}")
    public Result<?> create(
            @PathVariable Long connectionId,
            @RequestBody EdgeSaveRequest req) {
        permissionChecker.require("edge_data:create");
        service.create(connectionId, req);
        return Result.ok();
    }

    @PutMapping("/{connectionId}/edge/{edgeId}")
    public Result<?> update(
            @PathVariable Long connectionId,
            @PathVariable String edgeId,
            @RequestBody EdgeSaveRequest req) {
        permissionChecker.require("edge_data:update");
        service.update(connectionId, edgeId, req);
        return Result.ok();
    }

    @DeleteMapping("/{connectionId}/edge/{edgeId}")
    public Result<?> delete(
            @PathVariable Long connectionId,
            @PathVariable String edgeId) {
        permissionChecker.require("edge_data:delete");
        service.delete(connectionId, edgeId);
        return Result.ok();
    }

    @PostMapping("/{connectionId}/batch-delete")
    public Result<Map<String, Object>> batchDelete(
            @PathVariable Long connectionId,
            @RequestBody Map<String, Object> body) {
        permissionChecker.require("edge_data:batch_delete");
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
        permissionChecker.require("edge_data:clear");
        long count = service.clearEdges(connectionId, schema, label);
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
        return Result.ok(service.exportEdges(connectionId, schema, label, filters, format));
    }

    @GetMapping("/gremlin-examples/{schema}/{label}")
    public Result<List<Map<String, String>>> gremlinExamples(
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.generateGremlinExamples(schema, label));
    }

    @GetMapping("/{connectionId}/vertices/{schema}/{label}")
    public Result<Map<String, Object>> searchVertices(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return Result.ok(service.vertexSearch(connectionId, schema, label, page, size, search));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
