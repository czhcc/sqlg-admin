package com.trs.modules.workspace;

import com.trs.common.Result;
import com.trs.modules.workspace.dto.AlertItemDto;
import com.trs.modules.workspace.dto.ConnectionStatusDto;
import com.trs.modules.workspace.dto.WorkspaceOverviewDto;
import com.trs.modules.workspace.service.WorkspaceService;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台 REST 控制器,提供概览数据、连接状态、异常提醒等接口。
 *
 * @author czh
 * @date 2026/07/23
 */
@RestController
@RequestMapping("/workspace")
public class WorkspaceController {

    private final WorkspaceService service;
    private final UserPreferenceService preferenceService;

    public WorkspaceController(WorkspaceService service, UserPreferenceService preferenceService) {
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
                    u.getId(), UserPreferenceService.KEY_WORKSPACE_ACTIVE_CONNECTION);
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
                    UserPreferenceService.KEY_WORKSPACE_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_WORKSPACE_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_WORKSPACE_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    @GetMapping("/overview/{connectionId}")
    public Result<WorkspaceOverviewDto> overview(@PathVariable Long connectionId) {
        return Result.ok(service.getOverview(connectionId));
    }

    @GetMapping("/connection-status")
    public Result<List<ConnectionStatusDto>> connectionStatus() {
        return Result.ok(service.getConnectionStatus());
    }

    @GetMapping("/alerts/{connectionId}")
    public Result<List<AlertItemDto>> alerts(@PathVariable Long connectionId) {
        return Result.ok(service.getAlerts(connectionId));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
