package com.trs.modules.gremlin;

import com.trs.common.Result;
import com.trs.modules.gremlin.entity.GremlinQueryFavorite;
import com.trs.modules.gremlin.entity.GremlinQueryHistory;
import com.trs.modules.gremlin.service.GremlinConsoleService;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gremlin 控制台 REST 控制器,提供查询执行、历史记录和收藏管理接口。
 *
 * @author czh
 * @date 2026/07/10
 */
@RestController
@RequestMapping("/gremlin")
public class GremlinConsoleController {

    private final GremlinConsoleService service;
    private final UserPreferenceService preferenceService;

    public GremlinConsoleController(GremlinConsoleService service, UserPreferenceService preferenceService) {
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
                    u.getId(), UserPreferenceService.KEY_GREMLIN_ACTIVE_CONNECTION);
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
                    UserPreferenceService.KEY_GREMLIN_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_GREMLIN_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_GREMLIN_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    @PostMapping("/{connectionId}/execute")
    public Result<Map<String, Object>> execute(
            @PathVariable Long connectionId,
            @RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        String query = str(body, "query");
        String mode = str(body, "mode");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        return Result.ok(service.execute(connectionId, u.getId(), query, mode, params));
    }

    @GetMapping("/{connectionId}/history")
    public Result<List<GremlinQueryHistory>> history(@PathVariable Long connectionId) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        return Result.ok(service.getHistory(u.getId(), connectionId));
    }

    @DeleteMapping("/history/{id}")
    public Result<?> deleteHistory(@PathVariable Long id) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        service.deleteHistory(u.getId(), id);
        return Result.ok();
    }

    @DeleteMapping("/{connectionId}/history")
    public Result<?> clearHistory(@PathVariable Long connectionId) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        service.clearHistory(u.getId(), connectionId);
        return Result.ok();
    }

    @GetMapping("/favorites")
    public Result<List<GremlinQueryFavorite>> favorites() {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        return Result.ok(service.getFavorites(u.getId()));
    }

    @PostMapping("/favorites")
    public Result<?> addFavorite(@RequestBody GremlinQueryFavorite fav) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        service.addFavorite(u.getId(), fav);
        return Result.ok();
    }

    @PutMapping("/favorites/{id}")
    public Result<?> updateFavorite(@PathVariable Long id, @RequestBody GremlinQueryFavorite fav) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        service.updateFavorite(u.getId(), id, fav);
        return Result.ok();
    }

    @DeleteMapping("/favorites/{id}")
    public Result<?> deleteFavorite(@PathVariable Long id) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        service.deleteFavorite(u.getId(), id);
        return Result.ok();
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
