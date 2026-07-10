-- =============================================================================
-- V4__gremlin_query.sql
-- Gremlin 控制台: 查询历史 + 收藏查询
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_gremlin_query_history (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    connection_id  BIGINT        NOT NULL,
    query_text     TEXT          NOT NULL,
    mode           VARCHAR(20)   NOT NULL DEFAULT 'READONLY',
    success        BOOLEAN       NOT NULL DEFAULT TRUE,
    error_message  TEXT,
    cost_ms        INTEGER,
    result_count   INTEGER,
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gremlin_history_user_conn ON sys_gremlin_query_history (user_id, connection_id, create_time DESC);

COMMENT ON TABLE  sys_gremlin_query_history IS 'Gremlin 查询历史表';
COMMENT ON COLUMN sys_gremlin_query_history.id            IS '主键ID, 自增';
COMMENT ON COLUMN sys_gremlin_query_history.user_id       IS '执行用户ID';
COMMENT ON COLUMN sys_gremlin_query_history.connection_id IS '图连接ID';
COMMENT ON COLUMN sys_gremlin_query_history.query_text    IS 'Gremlin 查询语句';
COMMENT ON COLUMN sys_gremlin_query_history.mode          IS '执行模式: READONLY/READWRITE/ADMIN';
COMMENT ON COLUMN sys_gremlin_query_history.success       IS '是否执行成功';
COMMENT ON COLUMN sys_gremlin_query_history.error_message IS '错误信息(失败时)';
COMMENT ON COLUMN sys_gremlin_query_history.cost_ms       IS '执行耗时, 毫秒';
COMMENT ON COLUMN sys_gremlin_query_history.result_count  IS '返回结果数量';
COMMENT ON COLUMN sys_gremlin_query_history.create_time   IS '执行时间';

CREATE TABLE IF NOT EXISTS sys_gremlin_query_favorite (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    query_text     TEXT          NOT NULL,
    description    TEXT,
    mode           VARCHAR(20)   NOT NULL DEFAULT 'READONLY',
    sort_order     INTEGER       NOT NULL DEFAULT 0,
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gremlin_fav_user_title UNIQUE (user_id, title)
);

COMMENT ON TABLE  sys_gremlin_query_favorite IS 'Gremlin 收藏查询表';
COMMENT ON COLUMN sys_gremlin_query_favorite.id          IS '主键ID, 自增';
COMMENT ON COLUMN sys_gremlin_query_favorite.user_id     IS '用户ID';
COMMENT ON COLUMN sys_gremlin_query_favorite.title       IS '收藏标题, 唯一(按用户)';
COMMENT ON COLUMN sys_gremlin_query_favorite.query_text  IS 'Gremlin 查询语句';
COMMENT ON COLUMN sys_gremlin_query_favorite.description IS '描述说明';
COMMENT ON COLUMN sys_gremlin_query_favorite.mode        IS '推荐执行模式: READONLY/READWRITE/ADMIN';
COMMENT ON COLUMN sys_gremlin_query_favorite.sort_order  IS '排序序号';
COMMENT ON COLUMN sys_gremlin_query_favorite.create_time IS '创建时间';
COMMENT ON COLUMN sys_gremlin_query_favorite.update_time IS '更新时间';
