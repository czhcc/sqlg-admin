package com.trs.modules.connection;

import com.trs.common.Result;
import com.trs.modules.connection.service.DemoDataService;
import com.trs.security.RequirePermission;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图连接演示数据创建接口。
 *
 * @author codexsandboxoffline
 * @date 2026/1013
 */
@RestController
@RequestMapping("/connection")
public class DemoDataController {

    private final DemoDataService service;

    /**
     * 创建演示数据控制器。
     *
     * @param service 演示数据服务
     */
    public DemoDataController(DemoDataService service) {
        this.service = service;
    }

    /**
     * 在当前用户可见的连接中创建 Modern 示例图。
     *
     * @param id 目标连接 ID
     * @return 创建结果
     */
    @RequirePermission(menu = "connection", code = "connection:demo", name = "创建演示数据")
    @PostMapping("/{id}/demo-data")
    public Result<?> create(@PathVariable Long id) {
        service.create(id);
        return Result.ok();
    }
}
