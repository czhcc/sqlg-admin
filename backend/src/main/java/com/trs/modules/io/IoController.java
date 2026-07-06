package com.trs.modules.io;

import com.trs.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/io")
public class IoController {

    @PostMapping("/import")
    public Result<?> doImport(@RequestParam("file") MultipartFile file) {
        return Result.ok(Map.of("imported", 0));
    }

    @GetMapping("/export")
    public Result<?> export(@RequestParam(defaultValue = "graphml") String format) {
        return Result.ok(Map.of("url", ""));
    }
}
