-- =============================================================================
-- V1__init_schema.sql
-- 图数据库管理平台 - 初始 schema
-- 包含: sys_user / sys_operation_log / sys_graph_connection
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 系统用户表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGSERIAL    PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    password     VARCHAR(128) NOT NULL,
    nickname     VARCHAR(64),
    email        VARCHAR(128),
    phone        VARCHAR(32),
    status       SMALLINT     NOT NULL DEFAULT 1,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id          IS '主键ID, 自增';
COMMENT ON COLUMN sys_user.username    IS '登录用户名, 唯一';
COMMENT ON COLUMN sys_user.password    IS '密码, BCrypt 加密';
COMMENT ON COLUMN sys_user.nickname    IS '昵称';
COMMENT ON COLUMN sys_user.email       IS '邮箱';
COMMENT ON COLUMN sys_user.phone       IS '手机号';
COMMENT ON COLUMN sys_user.status      IS '状态: 1=启用 0=禁用';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';

-- 初始管理员 admin / 123456 (BCrypt hash)
INSERT INTO sys_user (username, password, nickname, status)
SELECT 'admin', '$2a$10$TKjX51eDAhsOXi978.Zkn.fHzxxHDOq8.0EFWXqa/ntogu0AsfFNG', 'Administrator', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- -----------------------------------------------------------------------------
-- 操作日志表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(64),
    module       VARCHAR(64),
    action       VARCHAR(256),
    method       VARCHAR(10),
    params       TEXT,
    result       TEXT,
    ip           VARCHAR(64),
    cost_ms      INTEGER,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_operation_log IS '操作日志表';
COMMENT ON COLUMN sys_operation_log.id          IS '主键ID, 自增';
COMMENT ON COLUMN sys_operation_log.user_id     IS '操作用户ID';
COMMENT ON COLUMN sys_operation_log.username    IS '操作用户名';
COMMENT ON COLUMN sys_operation_log.module      IS '操作模块';
COMMENT ON COLUMN sys_operation_log.action      IS '操作动作描述';
COMMENT ON COLUMN sys_operation_log.method      IS 'HTTP 方法: GET/POST/PUT/DELETE';
COMMENT ON COLUMN sys_operation_log.params      IS '请求参数 (JSON)';
COMMENT ON COLUMN sys_operation_log.result      IS '操作结果 (JSON/文本)';
COMMENT ON COLUMN sys_operation_log.ip          IS '请求来源 IP';
COMMENT ON COLUMN sys_operation_log.cost_ms     IS '耗时, 毫秒';
COMMENT ON COLUMN sys_operation_log.create_time IS '操作时间';

-- -----------------------------------------------------------------------------
-- 图数据库连接配置表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_graph_connection (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(128)  NOT NULL UNIQUE,
    db_type       VARCHAR(32)   NOT NULL,
    jdbc_url      VARCHAR(512)  NOT NULL,
    username      VARCHAR(64)   NOT NULL,
    password      VARCHAR(128)  NOT NULL,
    distributed   BOOLEAN       NOT NULL DEFAULT FALSE,
    pool_config   TEXT,
    remark        TEXT,
    status        SMALLINT      NOT NULL DEFAULT 1,
    is_default    BOOLEAN       NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_graph_connection IS '图数据库连接配置表';
COMMENT ON COLUMN sys_graph_connection.id          IS '主键ID, 自增';
COMMENT ON COLUMN sys_graph_connection.name        IS '连接名称, 唯一';
COMMENT ON COLUMN sys_graph_connection.db_type     IS '数据库类型: POSTGRES/H2/HSQLDB/MARIADB/MYSQL';
COMMENT ON COLUMN sys_graph_connection.jdbc_url    IS 'JDBC URL';
COMMENT ON COLUMN sys_graph_connection.username    IS '数据库用户名';
COMMENT ON COLUMN sys_graph_connection.password    IS '数据库密码';
COMMENT ON COLUMN sys_graph_connection.distributed IS '是否 distributed=true (sqlg 多 JVM 支持)';
COMMENT ON COLUMN sys_graph_connection.pool_config IS '连接池配置 (JSON, 如 maximumPoolSize/minimumIdle)';
COMMENT ON COLUMN sys_graph_connection.remark      IS '备注';
COMMENT ON COLUMN sys_graph_connection.status      IS '状态: 1=启用 0=停用';
COMMENT ON COLUMN sys_graph_connection.is_default  IS '是否默认连接';
COMMENT ON COLUMN sys_graph_connection.create_time IS '创建时间';
COMMENT ON COLUMN sys_graph_connection.update_time IS '更新时间';
