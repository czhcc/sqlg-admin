package com.trs.modules.diagnostic;

import com.trs.common.Result;
import com.trs.modules.diagnostic.service.DiagnosticService;
import com.trs.modules.task.entity.Task;
import com.trs.security.RequirePermission;
import com.trs.user.entity.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断工具 REST 控制器,提供检查项元数据、连接列表、提交诊断任务、查询报告等接口。
 * <p>
 * 所有接口要求登录,提交诊断任务需要 {@code diagnostic_tools:run} 操作权限。
 *
 * @author czh
 * @date 2026/0728
 */
@RestController
@RequestMapping("/diagnostic")
public class DiagnosticController {

    private final DiagnosticService service;

    public DiagnosticController(DiagnosticService service) {
        this.service = service;
    }

    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        return Result.ok(service.listConnections());
    }

    @GetMapping("/checks")
    public Result<List<Map<String, Object>>> checks() {
        return Result.ok(service.listChecks());
    }

    @RequirePermission(menu = "diagnostic-tools", code = "diagnostic_tools:run", name = "执行诊断")
    @PostMapping("/run")
    public Result<Task> run(@RequestBody Map<String, Object> body) {
        Long connectionId = body.get("connectionId") instanceof Number n ? n.longValue() : null;
        Object checkIdsObj = body.get("checkIds");
        if (!(checkIdsObj instanceof List<?> checkIds)) {
            return Result.fail(400, "checkIds 必须是数组");
        }
        List<String> checkIdsStr = checkIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
        String name = body.get("name") instanceof String s ? s : null;
        User user = currentUser();
        if (user == null) return Result.fail(401, "未登录");
        return Result.ok(service.runChecks(connectionId, checkIdsStr, name, user));
    }

    @GetMapping("/report/{taskId}")
    public Result<Map<String, Object>> report(@PathVariable Long taskId) {
        return Result.ok(service.getReport(taskId));
    }

    @GetMapping("/reports")
    public Result<Map<String, Object>> reports(
            @RequestParam(required = false) Long connectionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(service.listRecentReports(connectionId, page, size));
    }

    @RequirePermission(menu = "diagnostic-tools", code = "diagnostic_tools:run", name = "执行诊断")
    @PostMapping("/run-single")
    public Result<Map<String, Object>> runSingle(@RequestBody Map<String, Object> body) {
        Long connectionId = body.get("connectionId") instanceof Number n ? n.longValue() : null;
        String checkId = body.get("checkId") instanceof String s ? s : null;
        if (connectionId == null) return Result.fail(400, "connectionId 不能为空");
        if (checkId == null || checkId.isBlank()) return Result.fail(400, "checkId 不能为空");
        return Result.ok(service.runSingleCheck(connectionId, checkId));
    }

    @GetMapping("/report/{taskId}/export")
    public ResponseEntity<String> exportReport(@PathVariable Long taskId) {
        String json = service.exportReportAsJson(taskId);
        String fileName = service.getReportFileName(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/report/{taskId}/export-excel")
    public ResponseEntity<byte[]> exportReportExcel(@PathVariable Long taskId) {
        byte[] xlsx = service.exportReportAsExcel(taskId);
        String fileName = service.getReportExcelFileName(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam Long baselineTaskId,
            @RequestParam Long currentTaskId) {
        return Result.ok(service.compareReports(baselineTaskId, currentTaskId));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) Long connectionId,
            @RequestParam(defaultValue = "50") int recentLimit) {
        return Result.ok(service.aggregateStats(connectionId, recentLimit));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getPrincipal() instanceof User u ? u : null;
    }
}
