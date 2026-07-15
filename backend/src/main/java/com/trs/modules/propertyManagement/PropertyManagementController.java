package com.trs.modules.propertyManagement;

import com.trs.common.Result;
import com.trs.modules.propertyManagement.dto.PropertyDetailDto;
import com.trs.modules.propertyManagement.dto.PropertySaveRequest;
import com.trs.modules.propertyManagement.dto.PropertyUpdateRequest;
import com.trs.modules.propertyManagement.service.PropertyManagementService;
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
 * 属性管理 REST 控制器,提供 VertexLabel / EdgeLabel 上属性的列表、新增、删除、
 * 索引创建/删除及 UI 元数据管理。
 *
 * @author czh
 * @date 2026/07/08
 */
@RestController
@RequestMapping("/property-management")
public class PropertyManagementController {

    private final PropertyManagementService service;
    private final UserPreferenceService preferenceService;
    private final PermissionChecker permissionChecker;

    public PropertyManagementController(PropertyManagementService service,
                                        UserPreferenceService preferenceService,
                                        PermissionChecker permissionChecker) {
        this.service = service;
        this.preferenceService = preferenceService;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 获取可用连接列表,含用户上次选择的连接 ID。
     *
     * @return {connections: [...], activeConnectionId: Long|null}
     */
    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        List<Map<String, Object>> conns = service.listConnections();
        Long remembered = null;
        User u = currentUser();
        if (u != null) {
            remembered = preferenceService.getAsLong(
                    u.getId(), UserPreferenceService.KEY_PROPERTY_MANAGEMENT_ACTIVE_CONNECTION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", conns);
        result.put("activeConnectionId", remembered);
        return Result.ok(result);
    }

    /**
     * 记住用户选择的连接 ID。
     *
     * @param body {connectionId: Long|String|null}
     * @return 操作结果
     */
    @PutMapping("/active-connection")
    public Result<?> setActiveConnection(@RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        Object v = body.get("connectionId");
        if (v == null || "".equals(v)) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_PROPERTY_MANAGEMENT_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_PROPERTY_MANAGEMENT_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_PROPERTY_MANAGEMENT_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    /**
     * 获取指定连接的简化拓扑树(仅 schema + label 名),用于左侧树渲染。
     *
     * @param connectionId 图数据库连接 ID
     * @return {schemas: [{name, vertexLabels:[...], edgeLabels:[...]}]}
     */
    @GetMapping("/{connectionId}/tree")
    public Result<Map<String, Object>> tree(@PathVariable Long connectionId) {
        return Result.ok(service.getTree(connectionId));
    }

    /**
     * 刷新指定连接的拓扑缓存。
     *
     * @param connectionId 图数据库连接 ID
     * @return 操作结果
     */
    @PostMapping("/{connectionId}/refresh")
    public Result<?> refresh(@PathVariable Long connectionId) {
        service.evict(connectionId);
        return Result.ok();
    }

    /**
     * 列出指定 label 的所有属性详情。
     *
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @return 属性详情列表
     */
    @GetMapping("/{connectionId}/{kind}/{schema}/{label}")
    public Result<List<PropertyDetailDto>> listProperties(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.listProperties(connectionId, kind, schema, label));
    }

    /**
     * 新增属性。
     *
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @param req          请求体
     * @return 操作结果
     */
    @PostMapping("/{connectionId}/{kind}/{schema}/{label}")
    public Result<?> addProperty(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestBody PropertySaveRequest req) {
        permissionChecker.require("property:create");
        service.addProperty(connectionId, kind, schema, label, req);
        return Result.ok();
    }

    /**
     * 编辑属性元数据(属性名和类型不可改)。
     *
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @param propertyName 属性名
     * @param req          请求体
     * @return 操作结果
     */
    @PutMapping("/{connectionId}/{kind}/{schema}/{label}/{propertyName}")
    public Result<?> updateProperty(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label,
            @PathVariable String propertyName,
            @RequestBody PropertyUpdateRequest req) {
        permissionChecker.require("property:update");
        service.updatePropertyMeta(connectionId, kind, schema, label, propertyName, req);
        return Result.ok();
    }

    /**
     * 删除属性。
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @param body         {propertyName: String}
     * @return 操作结果
     */
    @DeleteMapping("/{connectionId}/{kind}/{schema}/{label}")
    public Result<?> removeProperty(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestBody Map<String, String> body) {
        permissionChecker.require("property:delete");
        String propertyName = body.get("propertyName");
        if (propertyName == null || propertyName.isBlank()) {
            return Result.fail(400, "propertyName 不能为空");
        }
        service.removeProperty(connectionId, kind, schema, label, propertyName);
        return Result.ok();
    }

    /**
     * 创建索引。
     *
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @param body         {propertyName: String, unique: boolean}
     * @return 操作结果
     */
    @PostMapping("/{connectionId}/{kind}/{schema}/{label}/index")
    public Result<?> createIndex(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestBody Map<String, Object> body) {
        permissionChecker.require("property:index");
        String propertyName = (String) body.get("propertyName");
        if (propertyName == null || propertyName.isBlank()) {
            return Result.fail(400, "propertyName 不能为空");
        }
        boolean unique = Boolean.TRUE.equals(body.get("unique"));
        service.createIndex(connectionId, kind, schema, label, propertyName, unique);
        return Result.ok();
    }

    /**
     * 删除索引。
     *
     * @param connectionId 图数据库连接 ID
     * @param kind         vertex / edge
     * @param schema       schema 名称
     * @param label        label 名称
     * @param body         {propertyName: String}
     * @return 操作结果
     */
    @DeleteMapping("/{connectionId}/{kind}/{schema}/{label}/index")
    public Result<?> removeIndex(
            @PathVariable Long connectionId,
            @PathVariable String kind,
            @PathVariable String schema,
            @PathVariable String label,
            @RequestBody Map<String, String> body) {
        String propertyName = body.get("propertyName");
        if (propertyName == null || propertyName.isBlank()) {
            return Result.fail(400, "propertyName 不能为空");
        }
        service.removeIndex(connectionId, kind, schema, label, propertyName);
        return Result.ok();
    }

    /**
     * 获取可选属性类型列表。
     *
     * @return 类型名称列表
     */
    @GetMapping("/property-types")
    public Result<List<String>> propertyTypes() {
        return Result.ok(service.listSupportedPropertyTypes());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
