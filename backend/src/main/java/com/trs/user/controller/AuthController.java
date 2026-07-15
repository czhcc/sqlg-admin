package com.trs.user.controller;

import com.trs.common.Result;
import com.trs.config.JwtUtil;
import com.trs.modules.log.service.LoginLogService;
import com.trs.modules.log.service.OperationLogService;
import com.trs.user.entity.User;
import com.trs.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 REST 控制器,提供登录、登出、获取当前用户信息。
 *
 * @author czh
 * @date 2026/0714
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginLogService loginLogService;
    private final OperationLogService operationLogService;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          LoginLogService loginLogService, OperationLogService operationLogService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginLogService = loginLogService;
        this.operationLogService = operationLogService;
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.fail(400, "用户名或密码不能为空");
        }
        User user = userService.findByUsername(username);
        if (!userService.checkPassword(user, password)) {
            loginLogService.recordFailure(username, "用户名或密码错误");
            return Result.fail(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        userService.updateLastLoginTime(user.getId());
        loginLogService.recordSuccess(user.getId(), user.getUsername());
        try {
            operationLogService.log().module("认证").action("用户登录").httpMethod("POST")
                    .type("LOGIN").name("用户登录: " + username)
                    .status("SUCCESS").user(user.getId(), username).submit();
        } catch (Exception ignored) {}
        return Result.ok(Map.of(
                "token", token,
                "username", user.getUsername(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname()
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            try {
                operationLogService.log().module("认证").action("用户登出").httpMethod("POST")
                        .type("LOGOUT").name("用户登出: " + u.getUsername())
                        .status("SUCCESS").user(u.getId(), u.getUsername()).submit();
            } catch (Exception ignored) {}
        }
        SecurityContextHolder.clearContext();
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<?> info(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Result.fail(401, "未登录");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof User principalUser)) {
            return Result.fail(401, "未登录");
        }
        User user = userService.findById(principalUser.getId());
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        user.setPassword(null);
        return Result.ok(user);
    }

    @GetMapping("/permissions")
    public Result<?> permissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Result.fail(401, "未登录");
        Object principal = auth.getPrincipal();
        if (!(principal instanceof User principalUser)) return Result.fail(401, "未登录");
        return Result.ok(userService.getEffectivePermissions(principalUser.getId()));
    }

    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        userService.update(u.getId(),
                str(body.get("nickname")), str(body.get("email")),
                str(body.get("phone")), str(body.get("remark")), null);
        try {
            operationLogService.log().module("认证").action("修改个人信息").httpMethod("PUT")
                    .type("UPDATE").name("修改个人信息: " + u.getUsername())
                    .status("SUCCESS").user(u.getId(), u.getUsername()).submit();
        } catch (Exception ignored) {}
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<?> changePassword(@RequestBody Map<String, String> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        if (currentPassword == null || currentPassword.isBlank())
            return Result.fail(400, "当前密码不能为空");
        if (newPassword == null || newPassword.length() < 6)
            return Result.fail(400, "新密码至少 6 位");
        User fresh = userService.findById(u.getId());
        if (!userService.checkPassword(fresh, currentPassword))
            return Result.fail(400, "当前密码错误");
        userService.changePassword(u.getId(), newPassword);
        try {
            operationLogService.log().module("认证").action("修改密码").httpMethod("PUT")
                    .type("UPDATE").name("修改密码: " + u.getUsername())
                    .status("SUCCESS").user(u.getId(), u.getUsername()).submit();
        } catch (Exception ignored) {}
        return Result.ok();
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
