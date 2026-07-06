package com.trs.modules.graphExplore;

import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/graph-explore")
public class GraphExploreController {

    @PostMapping("/expand")
    public Result<?> expand(@RequestBody Map<String, Object> body) {
        return Result.ok(Map.of("nodes", List.of(), "edges", List.of()));
    }
}
