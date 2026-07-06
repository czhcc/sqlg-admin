package com.trs.modules.topology;

import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/topology")
public class TopologyController {

    @GetMapping
    public Result<?> overview() {
        return Result.ok(java.util.Map.of("vertexLabels", java.util.List.of(), "edgeLabels", java.util.List.of()));
    }
}
