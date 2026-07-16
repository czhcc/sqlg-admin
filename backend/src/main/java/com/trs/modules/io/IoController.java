package com.trs.modules.io;

import com.trs.common.Result;
import com.trs.modules.io.service.IoService;
import com.trs.security.RequirePermission;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导入导出 REST 控制器,提供点/边数据导出、导入预览和导入执行接口。
 *
 * @author czh
 * @date 2026/07/10
 */
@RestController
@RequestMapping("/io")
public class IoController {

    private final IoService service;
    private final UserPreferenceService preferenceService;

    public IoController(IoService service, UserPreferenceService preferenceService) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        List<Map<String, Object>> conns = service.listConnections();
        Long remembered = null;
        User u = currentUser();
        if (u != null) {
            remembered = preferenceService.getAsLong(
                    u.getId(), UserPreferenceService.KEY_IO_ACTIVE_CONNECTION);
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
                    UserPreferenceService.KEY_IO_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_IO_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_IO_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    @GetMapping("/{connectionId}/schemas")
    public Result<List<Map<String, Object>>> schemas(@PathVariable Long connectionId) {
        return Result.ok(service.getSchemas(connectionId));
    }

    @GetMapping("/{connectionId}/export/vertex/{schema}/{label}")
    public Result<Map<String, Object>> exportVertices(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestParam(defaultValue = "csv") String format) {
        return Result.ok(service.exportVertices(connectionId, schema, label, format));
    }

    @GetMapping("/{connectionId}/export/edge/{schema}/{label}")
    public Result<Map<String, Object>> exportEdges(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestParam(defaultValue = "csv") String format) {
        return Result.ok(service.exportEdges(connectionId, schema, label, format));
    }

    @GetMapping("/{connectionId}/export/topology")
    public Result<Map<String, Object>> exportTopology(@PathVariable Long connectionId) {
        return Result.ok(service.exportTopology(connectionId));
    }

    @RequirePermission(menu = "import-export", code = "io:topology_import", name = "导入 Topology")
    @PostMapping("/{connectionId}/import/topology")
    public Result<Map<String, Object>> importTopology(
            @PathVariable Long connectionId,
            @RequestBody Map<String, Object> body) {
        String content = str(body, "content");
        if (content == null || content.isBlank()) {
            return Result.fail(400, "Topology JSON 内容不能为空");
        }
        return Result.ok(service.importTopology(connectionId, content));
    }

    @PostMapping("/{connectionId}/import/preview")
    public Result<Map<String, Object>> previewImport(
            @PathVariable Long connectionId,
            @RequestBody Map<String, Object> body) {
        String content = str(body, "content");
        String format = str(body, "format");
        String type = str(body, "type");
        return Result.ok(service.previewImport(connectionId, content, format, type));
    }

    @RequirePermission(menu = "import-export", code = "io:import", name = "导入点数据")
    @PostMapping("/{connectionId}/import/vertices")
    public Result<Map<String, Object>> importVertices(
            @PathVariable Long connectionId,
            @RequestBody IoService.ImportVerticesRequest req) {
        return Result.ok(service.importVertices(connectionId, req));
    }

    @RequirePermission(menu = "import-export", code = "io:import", name = "导入边数据")
    @PostMapping("/{connectionId}/import/edges")
    public Result<Map<String, Object>> importEdges(
            @PathVariable Long connectionId,
            @RequestBody IoService.ImportEdgesRequest req) {
        return Result.ok(service.importEdges(connectionId, req));
    }

    @PostMapping("/{connectionId}/refresh")
    public Result<?> refresh(@PathVariable Long connectionId) {
        service.evict(connectionId);
        return Result.ok();
    }

    private String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
