package com.trs.modules.topology;

import com.trs.common.Result;
import com.trs.modules.topology.dto.TopologyDto;
import com.trs.modules.topology.service.TopologyService;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topology")
public class TopologyController {

    private final TopologyService service;
    private final UserPreferenceService preferenceService;

    public TopologyController(TopologyService service, UserPreferenceService preferenceService) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        List<Map<String, Object>> conns = service.listConnectionsForTopology();
        Long remembered = null;
        User u = currentUser();
        if (u != null) {
            remembered = preferenceService.getAsLong(
                    u.getId(), UserPreferenceService.KEY_TOPOLOGY_ACTIVE_CONNECTION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", conns);
        result.put("activeConnectionId", remembered);
        return Result.ok(result);
    }

    @GetMapping("/{connectionId}")
    public Result<TopologyDto> getTopology(@PathVariable Long connectionId) {
        return Result.ok(service.getTopology(connectionId));
    }

    @PostMapping("/{connectionId}/refresh")
    public Result<?> refresh(@PathVariable Long connectionId) {
        service.evict(connectionId);
        return Result.ok();
    }

    @PutMapping("/active-connection")
    public Result<?> setActiveConnection(@RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        Object v = body.get("connectionId");
        if (v == null || "".equals(v)) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_TOPOLOGY_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_TOPOLOGY_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_TOPOLOGY_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
