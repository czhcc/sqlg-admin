-- =============================================================================
-- V6__user_management.sql
-- 用户管理扩展: 角色列/备注/最近登录时间 + 登录日志表
-- =============================================================================

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS roles           VARCHAR(256);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS remark          TEXT;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS last_login_time TIMESTAMP;

COMMENT ON COLUMN sys_user.roles           IS '角色, 逗号分隔的 role key (SUPER_ADMIN,DB_ADMIN,DEVELOPER,DATA_MAINTAINER,READONLY_USER,OPERATOR)';
COMMENT ON COLUMN sys_user.remark          IS '备注';
COMMENT ON COLUMN sys_user.last_login_time IS '最近登录时间';

UPDATE sys_user SET roles = 'SUPER_ADMIN' WHERE username = 'admin' AND roles IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_roles ON sys_user (roles);

-- -----------------------------------------------------------------------------
-- 登录日志表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT,
    username      VARCHAR(64),
    login_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time   TIMESTAMP,
    client_ip     VARCHAR(64),
    user_agent    VARCHAR(512),
    result_status VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    fail_reason   VARCHAR(256)
);

COMMENT ON TABLE  sys_login_log IS '登录日志表';
COMMENT ON COLUMN sys_login_log.id            IS '主键ID, 自增';
COMMENT ON COLUMN sys_login_log.user_id       IS '用户ID (登录失败时可能为空)';
COMMENT ON COLUMN sys_login_log.username      IS '登录用户名';
COMMENT ON COLUMN sys_login_log.login_time    IS '登录时间';
COMMENT ON COLUMN sys_login_log.logout_time   IS '登出时间';
COMMENT ON COLUMN sys_login_log.client_ip     IS '客户端 IP';
COMMENT ON COLUMN sys_login_log.user_agent    IS '浏览器 User-Agent';
COMMENT ON COLUMN sys_login_log.result_status IS '结果: SUCCESS / FAILED';
COMMENT ON COLUMN sys_login_log.fail_reason   IS '失败原因';

CREATE INDEX IF NOT EXISTS idx_login_log_time ON sys_login_log (login_time DESC);
CREATE INDEX IF NOT EXISTS idx_login_log_user ON sys_login_log (user_id);
CREATE INDEX IF NOT EXISTS idx_login_log_status ON sys_login_log (result_status);
