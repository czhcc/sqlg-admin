package com.trs.modules.graphAlgorithm;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.security.PermissionChecker;
import org.springframework.stereotype.Component;

/**
 * 图算法入口与任务执行共用的权限、连接范围校验。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@Component
public class AlgorithmAccess {
    private final PermissionChecker permissions;
    private final ConnectionVisibilityHelper visibility;

    /** 创建算法访问校验器。 */
    public AlgorithmAccess(PermissionChecker permissions, ConnectionVisibilityHelper visibility) {
        this.permissions = permissions;
        this.visibility = visibility;
    }

    /** 根据当前线程用户重新校验操作权限及连接可见性。 */
    public void require(Long connectionId, String operation) {
        permissions.require("graph_algorithm:" + operation);
        if (connectionId == null || visibility.listEnabledForCurrentUser().stream()
                .noneMatch(connection -> connectionId.equals(connection.getId())))
            throw new SecurityException("连接不存在、已停用或不可见");
    }
}
