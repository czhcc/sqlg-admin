package com.trs.modules.connection;

import com.trs.common.Result;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.service.GraphConnectionService;
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

    @PostMapping
    public Result<GraphConnection> create(@RequestBody GraphConnection body) {
        return Result.ok(service.create(body));
    }

    @PutMapping("/{id}")
    public Result<GraphConnection> update(@PathVariable Long id, @RequestBody GraphConnection body) {
        return Result.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/test")
    public Result<GraphConnectionService.TestResult> test(@RequestBody GraphConnection body) {
        return Result.ok(service.test(body));
    }

    @PostMapping("/{id}/test")
    public Result<GraphConnectionService.TestResult> testById(@PathVariable Long id) {
        GraphConnection c = service.getById(id);
        if (c == null) return Result.fail(404, "连接不存在");
        c.setId(id);
        c.setPassword(null);
        return Result.ok(service.test(c));
    }

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

    @PutMapping("/{id}/default")
    public Result<?> setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Result.ok();
    }
}
