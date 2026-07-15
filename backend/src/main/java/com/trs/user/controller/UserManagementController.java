package com.trs.user.controller;

import com.trs.common.Result;
import com.trs.modules.log.service.OperationLogService;
import com.trs.user.entity.RoleDefinition;
import com.trs.user.entity.User;
import com.trs.user.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 REST 控制器,提供用户列表、详情、CRUD、角色分配、权限查询等接口。
 *
 * @author czh
 * @date 2026/0714
 */
@RestController
@RequestMapping("/user/management")
public class UserManagementController {

    private final UserService userService;
    private final OperationLogService logService;

    public UserManagementController(UserService userService, OperationLogService logService) {
        this.userService = userService;
        this.logService = logService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short status) {
        return Result.ok(userService.page(page, size, keyword, status));
    }

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles() {
        return Result.ok(RoleDefinition.all().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", r.getKey());
                    m.put("label", r.getLabel());
                    m.put("description", r.getDescription());
                    m.put("menuPermissions", r.getMenuPermissions());
                    m.put("operationPermissions", r.getOperationPermissions());
                    m.put("gremlinPermission", r.getGremlinPermission());
                    m.put("allowDangerousOps", r.isAllowDangerousOps());
                    return m;
                })
                .toList());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(userService.getDetail(id));
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        Long id = userService.create(
                username, password,
                str(body.get("nickname")), str(body.get("email")),
                str(body.get("phone")),
                body.get("roles") instanceof List<?> list ? String.join(",", list.stream().map(String::valueOf).toList()) : str(body.get("roles")),
                str(body.get("remark")),
                body.get("status") instanceof Number n ? n.shortValue() : null
        );
        logCreate("新增用户", username);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Short status = body.get("status") instanceof Number n ? n.shortValue() : null;
        userService.update(id,
                str(body.get("nickname")), str(body.get("email")),
                str(body.get("phone")), str(body.get("remark")), status);
        logAction(id, "编辑用户", "UPDATE");
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Short status = body.get("status") instanceof Number n ? n.shortValue() : null;
        if (status == null) return Result.fail(400, "status 不能为空");
        userService.updateStatus(id, status);
        logAction(id, status == 1 ? "启用用户" : "停用用户", "UPDATE");
        return Result.ok();
    }

    @PutMapping("/{id}/password-reset")
    public Result<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String newPassword = str(body.get("password"));
        if (newPassword == null || newPassword.isBlank())
            return Result.fail(400, "password 不能为空");
        userService.resetPassword(id, newPassword);
        logAction(id, "重置密码", "UPDATE");
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    public Result<?> assignRoles(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> roleKeys = (List<String>) body.get("roles");
        if (roleKeys == null) return Result.fail(400, "roles 不能为空");
        userService.assignRoles(id, roleKeys);
        logAction(id, "分配角色", "UPDATE");
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(userService.getEffectivePermissions(id));
    }

    @GetMapping("/{id}/connections")
    public Result<Map<String, Object>> connections(@PathVariable Long id) {
        return Result.ok(userService.getConnectionPermissions(id));
    }

    @GetMapping("/{id}/login-logs")
    public Result<Map<String, Object>> loginLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getLoginLogs(id, page, size));
    }

    @GetMapping("/{id}/operation-logs")
    public Result<Map<String, Object>> operationLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        User u = userService.findById(id);
        if (u == null) return Result.fail(404, "用户不存在");
        return Result.ok(logService.page(page, size, null, null, null,
                null, u.getUsername(), null, startTime, endTime, null));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        User u = userService.findById(id);
        if (u == null) return Result.fail(404, "用户不存在");
        userService.delete(id);
        logService.log().module("用户管理")
                .action("删除用户: " + u.getUsername()).httpMethod("DELETE")
                .type("DELETE").name("删除用户: " + u.getUsername())
                .status("SUCCESS").objectType("User").objectName(u.getUsername())
                .objectId(String.valueOf(id)).submit();
        return Result.ok();
    }

    private void logAction(Long userId, String action, String type) {
        User actor = currentUser();
        try {
            logService.log().module("用户管理")
                    .action(action).httpMethod("PUT").type(type).name(action)
                    .status("SUCCESS").objectType("User")
                    .objectId(String.valueOf(userId)).submit();
        } catch (Exception ignored) {}
    }

    private void logCreate(String action, String username) {
        try {
            logService.log().module("用户管理")
                    .action(action + ": " + username).httpMethod("POST")
                    .type("CREATE").name(action + ": " + username)
                    .status("SUCCESS").objectType("User").objectName(username).submit();
        } catch (Exception ignored) {}
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
