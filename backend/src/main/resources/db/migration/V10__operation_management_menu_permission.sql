-- =============================================================================
-- V10__operation_management_menu_permission.sql
-- 为所有已有角色追加运维管理 3 个菜单权限(任务中心/诊断工具/运维监控)
-- =============================================================================

UPDATE sys_role
SET menu_permissions = CASE
    WHEN menu_permissions IS NULL OR menu_permissions IN ('', '[]')
        THEN '["task-center","diagnostic-tools","operation-monitor"]'
    ELSE '["task-center","diagnostic-tools","operation-monitor",' || substring(menu_permissions FROM 2)
END
WHERE menu_permissions IS NULL
   OR menu_permissions NOT LIKE '%task-center%';
