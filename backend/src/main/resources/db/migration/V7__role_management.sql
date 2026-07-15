-- =============================================================================
-- V7__role_management.sql
-- 角色管理: sys_role 表 + sys_role_connection_auth 表 + 种子数据
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_role (
    id                     BIGSERIAL    PRIMARY KEY,
    role_key               VARCHAR(64)  NOT NULL UNIQUE,
    role_name              VARCHAR(64)  NOT NULL,
    description            TEXT,
    status                 SMALLINT     NOT NULL DEFAULT 1,
    is_builtin             BOOLEAN      NOT NULL DEFAULT FALSE,
    menu_permissions       TEXT,
    operation_permissions  TEXT,
    gremlin_permission     VARCHAR(20)  NOT NULL DEFAULT 'READ_ONLY',
    dangerous_permissions  TEXT,
    connection_default     VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    create_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_role IS '角色定义表';
COMMENT ON COLUMN sys_role.id                    IS '主键ID, 自增';
COMMENT ON COLUMN sys_role.role_key              IS '角色编码, 唯一';
COMMENT ON COLUMN sys_role.role_name              IS '角色名称';
COMMENT ON COLUMN sys_role.description            IS '角色说明';
COMMENT ON COLUMN sys_role.status                 IS '状态: 1=启用 0=停用';
COMMENT ON COLUMN sys_role.is_builtin             IS '是否内置角色(内置角色不可删除)';
COMMENT ON COLUMN sys_role.menu_permissions       IS '菜单权限 JSON 数组';
COMMENT ON COLUMN sys_role.operation_permissions  IS '操作权限 JSON 数组';
COMMENT ON COLUMN sys_role.gremlin_permission     IS 'Gremlin 权限: READ_ONLY / WRITE / DANGEROUS';
COMMENT ON COLUMN sys_role.dangerous_permissions  IS '危险操作权限 JSON 数组';
COMMENT ON COLUMN sys_role.connection_default     IS '默认连接权限: NONE / READ / WRITE / ADMIN';
COMMENT ON COLUMN sys_role.create_time            IS '创建时间';
COMMENT ON COLUMN sys_role.update_time            IS '更新时间';

CREATE TABLE IF NOT EXISTS sys_role_connection_auth (
    role_id       BIGINT      NOT NULL,
    connection_id BIGINT      NOT NULL,
    access_level  VARCHAR(20) NOT NULL DEFAULT 'NONE',
    PRIMARY KEY (role_id, connection_id)
);

COMMENT ON TABLE  sys_role_connection_auth IS '角色-连接授权表';
COMMENT ON COLUMN sys_role_connection_auth.role_id      IS '角色ID';
COMMENT ON COLUMN sys_role_connection_auth.connection_id IS '连接ID';
COMMENT ON COLUMN sys_role_connection_auth.access_level  IS '访问级别: NONE / READ / WRITE / ADMIN';

-- 种子数据: 5 个缺省角色
INSERT INTO sys_role (role_key, role_name, description, status, is_builtin, menu_permissions, operation_permissions, gremlin_permission, dangerous_permissions, connection_default) VALUES
(
    'DB_ADMIN', '图库管理员', '管理图库结构、点边数据、导入导出,可执行危险操作',
    1, true,
    '["connection","topology","vertex-type","edge-type","property-management","vertex-data","edge-data","graph-explore","gremlin","import-export","user-management","role-management","permission-overview","login-log","operation-log"]',
    '["connection:view","connection:create","connection:update","connection:delete","connection:test","topology:view","topology:refresh","vertex_type:view","vertex_type:create","vertex_type:update","vertex_type:delete","edge_type:view","edge_type:create","edge_type:delete","property:view","property:create","property:update","property:delete","property:index","vertex_data:view","vertex_data:create","vertex_data:update","vertex_data:delete","vertex_data:batch_delete","vertex_data:clear","edge_data:view","edge_data:create","edge_data:update","edge_data:delete","edge_data:batch_delete","edge_data:clear","graph_explore:view","graph_explore:expand","gremlin:execute","io:export","io:import","io:topology_export","io:topology_import","user:view","user:create","user:update","user:delete","user:reset_password","user:assign_roles","role:view","role:update","permission:view","login_log:view","operation_log:view"]',
    'DANGEROUS',
    '["dangerous:connection_delete","dangerous:topology_delete_schema","dangerous:topology_delete_vertex_label","dangerous:topology_delete_edge_label","dangerous:topology_delete_property","dangerous:topology_delete_index","dangerous:vertex_batch_delete","dangerous:vertex_clear","dangerous:edge_batch_delete","dangerous:edge_clear","dangerous:gremlin_drop","dangerous:io_overwrite","dangerous:io_topology_overwrite"]',
    'ADMIN'
) ON CONFLICT (role_key) DO NOTHING;

INSERT INTO sys_role (role_key, role_name, description, status, is_builtin, menu_permissions, operation_permissions, gremlin_permission, dangerous_permissions, connection_default) VALUES
(
    'DEVELOPER', '开发人员', '开发调试用: 管理 Topology、点边类型与数据、Gremlin 查询',
    1, true,
    '["topology","vertex-type","edge-type","property-management","vertex-data","edge-data","graph-explore","gremlin","import-export"]',
    '["topology:view","topology:refresh","vertex_type:view","vertex_type:create","vertex_type:update","vertex_type:delete","edge_type:view","edge_type:create","edge_type:delete","property:view","property:create","property:update","property:delete","property:index","vertex_data:view","vertex_data:create","vertex_data:update","vertex_data:delete","edge_data:view","edge_data:create","edge_data:update","edge_data:delete","graph_explore:view","graph_explore:expand","gremlin:execute","io:export","io:import"]',
    'WRITE',
    '[]',
    'WRITE'
) ON CONFLICT (role_key) DO NOTHING;

INSERT INTO sys_role (role_key, role_name, description, status, is_builtin, menu_permissions, operation_permissions, gremlin_permission, dangerous_permissions, connection_default) VALUES
(
    'DATA_MAINTAINER', '数据维护人员', '维护点边数据,支持批量导入导出与清空',
    1, true,
    '["topology","vertex-data","edge-data","import-export"]',
    '["topology:view","vertex_data:view","vertex_data:create","vertex_data:update","vertex_data:delete","vertex_data:batch_delete","vertex_data:clear","edge_data:view","edge_data:create","edge_data:update","edge_data:delete","edge_data:batch_delete","edge_data:clear","io:export","io:import"]',
    'WRITE',
    '["dangerous:vertex_batch_delete","dangerous:vertex_clear","dangerous:edge_batch_delete","dangerous:edge_clear","dangerous:io_overwrite"]',
    'WRITE'
) ON CONFLICT (role_key) DO NOTHING;

INSERT INTO sys_role (role_key, role_name, description, status, is_builtin, menu_permissions, operation_permissions, gremlin_permission, dangerous_permissions, connection_default) VALUES
(
    'READONLY_USER', '只读查询人员', '仅可查看 Topology、浏览图关系、执行只读 Gremlin',
    1, true,
    '["topology","graph-explore","gremlin"]',
    '["topology:view","graph_explore:view","graph_explore:expand","gremlin:execute"]',
    'READ_ONLY',
    '[]',
    'READ'
) ON CONFLICT (role_key) DO NOTHING;

INSERT INTO sys_role (role_key, role_name, description, status, is_builtin, menu_permissions, operation_permissions, gremlin_permission, dangerous_permissions, connection_default) VALUES
(
    'OPERATOR', '运维人员', '管理连接配置、查看操作日志、只读 Gremlin',
    1, true,
    '["connection","topology","import-export","operation-log"]',
    '["connection:view","connection:create","connection:update","connection:delete","connection:test","topology:view","io:export","io:import","operation_log:view"]',
    'READ_ONLY',
    '["dangerous:connection_delete"]',
    'ADMIN'
) ON CONFLICT (role_key) DO NOTHING;
