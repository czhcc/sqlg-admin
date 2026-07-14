package com.trs.modules.log;

import com.trs.common.Result;
import com.trs.modules.log.entity.OperationLog;
import com.trs.modules.log.service.OperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 REST 控制器,提供日志分页查询、详情和模块列表接口。
 *
 * @author czh
 * @date 2026/07/13
 */
@RestController
@RequestMapping("/operation-log")
public class OperationLogController {

    private final OperationLogService service;

    public OperationLogController(OperationLogService service) {
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean isDangerous,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(page, size, module, operationType, status, connectionId,
                username, isDangerous, startTime, endTime, keyword));
    }

    @GetMapping("/{id}")
    public Result<OperationLog> detail(@PathVariable Long id) {
        OperationLog log = service.getById(id);
        if (log == null) return Result.fail(404, "日志不存在");
        return Result.ok(log);
    }

    @GetMapping("/modules")
    public Result<List<String>> modules() {
        return Result.ok(service.listModules());
    }
}
