package com.trs.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 权限校验 AOP 切面,拦截所有标注 {@link RequirePermission} 的方法,
 * 在执行前校验当前用户是否拥有对应操作权限。
 *
 * @author czh
 * @date 2026/0715
 */
@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final PermissionChecker permissionChecker;

    public PermissionAspect(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        permissionChecker.require(requirePermission.code());
        return joinPoint.proceed();
    }
}
