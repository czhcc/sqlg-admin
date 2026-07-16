-- =============================================================================
-- V8__dangerous_qualification.sql
-- 将旧的 13 个细粒度危险操作权限码迁移到新的 5 个通用危险操作资格码
-- =============================================================================

UPDATE sys_role SET dangerous_permissions = '["dangerous:batch_delete","dangerous:data_clear","dangerous:topology_delete","dangerous:gremlin_execute","dangerous:import_overwrite"]'
WHERE role_key = 'DB_ADMIN';

UPDATE sys_role SET dangerous_permissions = '[]'
WHERE role_key = 'DEVELOPER';

UPDATE sys_role SET dangerous_permissions = '["dangerous:batch_delete","dangerous:data_clear","dangerous:import_overwrite"]'
WHERE role_key = 'DATA_MAINTAINER';

UPDATE sys_role SET dangerous_permissions = '[]'
WHERE role_key = 'READONLY_USER';

UPDATE sys_role SET dangerous_permissions = '[]'
WHERE role_key = 'OPERATOR';
