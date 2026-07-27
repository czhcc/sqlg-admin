-- =============================================================================
-- V9__workspace_menu_permission.sql
-- 为所有已有角色追加 workspace 菜单权限(工作台是登录后默认首页)
-- =============================================================================

UPDATE sys_role
SET menu_permissions = CASE
    WHEN menu_permissions IS NULL OR menu_permissions IN ('', '[]') THEN '["workspace"]'
    ELSE '["workspace",' || substring(menu_permissions FROM 2)
END
WHERE menu_permissions IS NULL
   OR menu_permissions NOT LIKE '%workspace%';
