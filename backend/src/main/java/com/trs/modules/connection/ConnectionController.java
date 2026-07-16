package com.trs.modules.connection;

import com.trs.common.Result;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.service.GraphConnectionService;
import com.trs.security.RequirePermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/connection")
public class ConnectionController {

    private final GraphConnectionService service;

    public ConnectionController(GraphConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<GraphConnection>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<GraphConnection> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @RequirePermission(menu = "connection", code = "connection:create", name = "新增连接")
    @PostMapping
    public Result<GraphConnection> create(@RequestBody GraphConnection body) {
        return Result.ok(service.create(body));
    }

    @RequirePermission(menu = "connection", code = "connection:update", name = "编辑连接")
    @PutMapping("/{id}")
    public Result<GraphConnection> update(@PathVariable Long id, @RequestBody GraphConnection body) {
        return Result.ok(service.update(id, body));
    }

    @RequirePermission(menu = "connection", code = "connection:delete", name = "删除连接")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @RequirePermission(menu = "connection", code = "connection:test", name = "测试连接")
    @PostMapping("/test")
    public Result<GraphConnectionService.TestResult> test(@RequestBody GraphConnection body) {
        return Result.ok(service.test(body));
    }

    @RequirePermission(menu = "connection", code = "connection:test", name = "测试连接")
    @PostMapping("/{id}/test")
    public Result<GraphConnectionService.TestResult> testById(@PathVariable Long id) {
        GraphConnection c = service.getById(id);
        if (c == null) return Result.fail(404, "连接不存在");
        c.setId(id);
        c.setPassword(null);
        return Result.ok(service.test(c));
    }

    @RequirePermission(menu = "connection", code = "connection:update", name = "启停连接")
    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object v = body.get("status");
        short status;
        if (v instanceof Number n) status = n.shortValue();
        else if (v instanceof String s) status = Short.parseShort(s);
        else return Result.fail(400, "status 字段必填");
        service.updateStatus(id, status);
        return Result.ok();
    }

    @RequirePermission(menu = "connection", code = "connection:update", name = "设为默认连接")
    @PutMapping("/{id}/default")
    public Result<?> setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Result.ok();
    }
}
