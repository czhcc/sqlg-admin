package com.trs.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上,声明该接口需要的操作权限。
 * <p>
 * 启动时由 {@link PermissionRegistry} 扫描所有标注此注解的方法,
 * 自动构建菜单-操作目录树,供角色权限配置界面渲染。
 * 运行时由 {@link PermissionAspect} 拦截,校验当前用户是否拥有对应操作权限。
 * 未标注此注解的接口不受权限控制。
 *
 * @author czh
 * @date 2026/0715
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 所属菜单 key,对应前端路由路径(去掉前导 /),如 "vertex-data"。
     */
    String menu();

    /**
     * 操作权限编码,如 "vertex_data:clear"。
     */
    String code();

    /**
     * 操作显示名称,如 "清空点数据"。
     */
    String name();
}
