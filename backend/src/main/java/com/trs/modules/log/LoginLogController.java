package com.trs.modules.log;

import com.trs.common.Result;
import com.trs.modules.log.service.LoginLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 登录日志 REST 控制器,提供登录日志的分页查询。
 *
 * @author czh
 * @date 2026/0714
 */
@RestController
@RequestMapping("/login-log")
public class LoginLogController {

    private final LoginLogService service;

    public LoginLogController(LoginLogService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(page, size, username, resultStatus, startTime, endTime, keyword));
    }
}
