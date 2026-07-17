package com.trs.user.controller;

import com.trs.common.Result;
import com.trs.security.RequirePermission;
import com.trs.user.service.PermissionOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 权限总览 REST 控制器,提供按用户和按角色查看权限的集中查询与分析接口。
 * <p>
 * 该控制器为只读接口,不修改任何权限配置。
 *
 * @author czh
 * @date 2026/0716
 */
@RestController
@RequestMapping("/permission/overview")
public class PermissionOverviewController {

    private final PermissionOverviewService service;

    public PermissionOverviewController(PermissionOverviewService service) {
        this.service = service;
    }

    /**
     * 搜索用户列表,供权限总览左侧选择。
     *
     * @param keyword 用户名/昵称/邮箱关键词
     * @param status  状态过滤: 1=启用 0=停用
     * @return 用户摘要列表
     */
    @GetMapping("/users")
    @RequirePermission(menu = "permission-overview", code = "permission:view", name = "查看权限总览")
    public Result<Map<String, Object>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short status) {
        return Result.ok(service.searchUsers(keyword, status));
    }

    /**
     * 获取指定用户的完整权限总览,包括菜单权限、操作权限、可见连接、
     * Gremlin 权限、危险操作资格和配置检查。
     *
     * @param id 用户ID
     * @return 权限总览数据
     */
    @GetMapping("/users/{id}")
    @RequirePermission(menu = "permission-overview", code = "permission:view", name = "查看权限总览")
    public Result<Map<String, Object>> userOverview(@PathVariable Long id) {
        return Result.ok(service.getUserPermissionOverview(id));
    }

    /**
     * 搜索角色列表,供权限总览左侧选择。
     *
     * @param keyword 角色编码/名称关键词
     * @param status  状态过滤: 1=启用 0=停用
     * @return 角色摘要列表
     */
    @GetMapping("/roles")
    @RequirePermission(menu = "permission-overview", code = "permission:view", name = "查看权限总览")
    public Result<Map<String, Object>> searchRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Short status) {
        return Result.ok(service.searchRoles(keyword, status));
    }

    /**
     * 获取指定角色的完整权限总览,包括角色成员、菜单权限、操作权限、
     * 可见连接、Gremlin 权限、危险操作资格和角色级配置检查。
     *
     * @param id 角色ID
     * @return 角色权限总览数据
     */
    @GetMapping("/roles/{id}")
    @RequirePermission(menu = "permission-overview", code = "permission:view", name = "查看权限总览")
    public Result<Map<String, Object>> roleOverview(@PathVariable Long id) {
        return Result.ok(service.getRolePermissionOverview(id));
    }
}
