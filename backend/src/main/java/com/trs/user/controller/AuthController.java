package com.trs.user.controller;

import com.trs.common.Result;
import com.trs.config.JwtUtil;
import com.trs.user.entity.User;
import com.trs.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.fail(400, "用户名或密码不能为空");
        }
        User user = userService.findByUsername(username);
        if (!userService.checkPassword(user, password)) {
            return Result.fail(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return Result.ok(Map.of(
                "token", token,
                "username", user.getUsername(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname()
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
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
}
