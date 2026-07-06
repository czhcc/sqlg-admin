package com.trs.modules.gremlin;

import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gremlin")
public class GremlinController {

    @PostMapping("/execute")
    public Result<?> execute(@RequestBody Map<String, String> body) {
        String script = body.get("script");
        return Result.ok(Map.of("results", List.of(), "script", script == null ? "" : script));
    }
}
