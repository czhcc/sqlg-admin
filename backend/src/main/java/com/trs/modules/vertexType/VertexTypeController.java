package com.trs.modules.vertexType;

import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vertex-type")
public class VertexTypeController {

    @GetMapping
    public Result<?> list() {
        return Result.ok(List.of());
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        return Result.ok(Map.of("id", 0));
    }

    @DeleteMapping("/{name}")
    public Result<?> delete(@PathVariable String name) {
        return Result.ok();
    }
}
