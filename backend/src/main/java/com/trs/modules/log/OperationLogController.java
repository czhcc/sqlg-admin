package com.trs.modules.log;

import com.trs.common.PageResult;
import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/operation-log")
public class OperationLogController {

    @GetMapping
    public Result<?> page(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageResult.of(0, List.of()));
    }
}
