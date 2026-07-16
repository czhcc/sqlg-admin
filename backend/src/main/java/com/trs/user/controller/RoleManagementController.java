package com.trs.user.controller;

import com.trs.common.Result;
import com.trs.modules.log.service.OperationLogService;
import com.trs.user.entity.User;
import com.trs.user.service.RoleManagementService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理 REST 控制器,提供角色列表、详情、权限配置、成员管理和连接授权接口。
 *
 * @author czh
 * @date 2026/0715
 */
@RestController
@RequestMapping("/role/management")
public class RoleManagementController {

    private final RoleManagementService service;
    private final OperationLogService logService;

    public RoleManagementController(RoleManagementService service, OperationLogService logService) {
        this.service = service;
        this.logService = logService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short status) {
        return Result.ok(service.page(keyword, status));
    }

    @GetMapping("/catalog")
    public Result<Map<String, Object>> catalog() {
        return Result.ok(service.getCatalog());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(service.getDetail(id));
    }

    @PutMapping("/{id}/basic")
    public Result<?> updateBasic(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Short status = body.get("status") instanceof Number n ? n.shortValue() : null;
        service.updateBasic(id, str(body.get("roleName")), str(body.get("description")), status);
        logAction(id, "编辑角色基本信息");
        return Result.ok();
    }

    @PutMapping("/{id}/menus")
    public Result<?> updateMenus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> menus = (List<String>) body.get("menus");
        service.updateMenuPermissions(id, menus);
        logAction(id, "更新菜单权限");
        return Result.ok();
    }

    @PutMapping("/{id}/operations")
    public Result<?> updateOperations(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> operations = (List<String>) body.get("operations");
        service.updateOperationPermissions(id, operations);
        logAction(id, "更新操作权限");
        return Result.ok();
    }

    @PutMapping("/{id}/gremlin")
    public Result<?> updateGremlin(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String level = str(body.get("level"));
        service.updateGremlinPermission(id, level);
        logAction(id, "更新 Gremlin 权限");
        return Result.ok();
    }

    @PutMapping("/{id}/dangerous")
    public Result<?> updateDangerous(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ops = (List<String>) body.get("operations");
        service.updateDangerousPermissions(id, ops);
        logAction(id, "更新危险操作权限");
        return Result.ok();
    }

    @GetMapping("/{id}/connections")
    public Result<Map<String, Object>> connections(@PathVariable Long id) {
        return Result.ok(service.getConnectionAuth(id));
    }

    @PutMapping("/{id}/connections/{connectionId}")
    public Result<?> updateConnectionAuth(@PathVariable Long id, @PathVariable Long connectionId,
                                            @RequestBody Map<String, Object> body) {
        boolean visible = Boolean.TRUE.equals(body.get("visible"));
        service.updateConnectionAuth(id, connectionId, visible);
        logAction(id, "更新连接可见性");
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<Map<String, Object>> members(@PathVariable Long id) {
        return Result.ok(service.getMembers(id));
    }

    @PostMapping("/{id}/members")
    public Result<?> addMembers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> userIds = (List<Number>) body.get("userIds");
        if (userIds == null || userIds.isEmpty())
            return Result.fail(400, "userIds 不能为空");
        service.addMembers(id, userIds.stream().map(Number::longValue).collect(Collectors.toList()));
        logAction(id, "添加角色成员");
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<?> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        service.removeMember(id, userId);
        logAction(id, "移除角色成员");
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        try {
            logService.log().module("角色管理").action("删除角色")
                    .httpMethod("DELETE").type("DELETE").name("删除角色 ID: " + id)
                    .status("SUCCESS").objectType("Role").objectId(String.valueOf(id)).submit();
        } catch (Exception ignored) {}
        return Result.ok();
    }

    private void logAction(Long roleId, String action) {
        try {
            logService.log().module("角色管理").action(action).httpMethod("PUT")
                    .type("UPDATE").name(action).status("SUCCESS")
                    .objectType("Role").objectId(String.valueOf(roleId)).submit();
        } catch (Exception ignored) {}
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
