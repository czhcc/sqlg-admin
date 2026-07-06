package com.trs.modules.vertexData;

import com.trs.common.PageResult;
import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vertex-data")
public class VertexDataController {

    @GetMapping
    public Result<?> page(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String label) {
        return Result.ok(PageResult.of(0, List.of()));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(Map.of());
    }
}
