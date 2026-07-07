-- =============================================================================
-- V2__user_preference.sql
-- 用户偏好设置 (按用户维度持久化 UI 选择, 如 Topology 页面选中的连接)
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_user_preference (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    pref_key      VARCHAR(64)   NOT NULL,
    pref_value    VARCHAR(512),
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_pref UNIQUE (user_id, pref_key)
);

COMMENT ON TABLE  sys_user_preference IS '用户偏好设置表';
COMMENT ON COLUMN sys_user_preference.id          IS '主键ID, 自增';
COMMENT ON COLUMN sys_user_preference.user_id     IS '用户ID';
COMMENT ON COLUMN sys_user_preference.pref_key    IS '偏好键, 如 topology.active_connection';
COMMENT ON COLUMN sys_user_preference.pref_value  IS '偏好值';
COMMENT ON COLUMN sys_user_preference.create_time IS '创建时间';
COMMENT ON COLUMN sys_user_preference.update_time IS '更新时间';
