package com.trs.modules.topology;

import com.trs.common.Result;
import com.trs.modules.topology.dto.TopologyDto;
import com.trs.modules.topology.service.TopologyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topology")
public class TopologyController {

    private final TopologyService service;

    public TopologyController(TopologyService service) {
        this.service = service;
    }

    @GetMapping("/connections")
    public Result<List<Map<String, Object>>> connections() {
        return Result.ok(service.listConnectionsForTopology());
    }

    @GetMapping("/{connectionId}")
    public Result<TopologyDto> getTopology(@PathVariable Long connectionId) {
        return Result.ok(service.getTopology(connectionId));
    }

    @PostMapping("/{connectionId}/refresh")
    public Result<?> refresh(@PathVariable Long connectionId) {
        service.evict(connectionId);
        return Result.ok();
    }
}
