-- =============================================================================
-- V11__task_center.sql
-- 任务中心: sys_task 表
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_task (
    id                  BIGSERIAL     PRIMARY KEY,
    task_no             VARCHAR(32)   NOT NULL UNIQUE,
    task_type           VARCHAR(32)   NOT NULL,
    category            VARCHAR(32)   NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    connection_id       BIGINT,
    connection_name     VARCHAR(128),
    schema_name         VARCHAR(128),
    target_label        VARCHAR(128),
    params              TEXT,
    result_summary      TEXT,
    error_message       TEXT,
    progress_percent    INT           NOT NULL DEFAULT 0,
    processed_count     BIGINT        NOT NULL DEFAULT 0,
    success_count       BIGINT        NOT NULL DEFAULT 0,
    failed_count        BIGINT        NOT NULL DEFAULT 0,
    total_count         BIGINT,
    current_stage       VARCHAR(128),
    result_file_path    VARCHAR(512),
    error_file_path     VARCHAR(512),
    parent_task_id      BIGINT,
    user_id             BIGINT,
    username            VARCHAR(64),
    create_time         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    last_heartbeat_time TIMESTAMP
);

CREATE INDEX idx_task_status        ON sys_task(status);
CREATE INDEX idx_task_connection    ON sys_task(connection_id);
CREATE INDEX idx_task_user          ON sys_task(user_id);
CREATE INDEX idx_task_create_time   ON sys_task(create_time DESC);
CREATE INDEX idx_task_heartbeat     ON sys_task(last_heartbeat_time);

COMMENT ON TABLE  sys_task                       IS '异步任务记录表';
COMMENT ON COLUMN sys_task.id                    IS '主键ID, 自增';
COMMENT ON COLUMN sys_task.task_no               IS '任务编号, 格式 TYPE-YYYYMMDD-NNN';
COMMENT ON COLUMN sys_task.task_type             IS '任务类型: IMPORT_VERTEX / EXPORT_EDGE / CLEAR_VERTEX 等';
COMMENT ON COLUMN sys_task.category              IS '任务分类: IMPORT / EXPORT / MAINTENANCE / STATISTICS / DIAGNOSTIC';
COMMENT ON COLUMN sys_task.name                  IS '任务名称, 可读描述';
COMMENT ON COLUMN sys_task.status                IS '状态: PENDING / RUNNING / SUCCESS / PARTIAL / FAILED / CANCELLED';
COMMENT ON COLUMN sys_task.connection_id         IS '关联连接ID';
COMMENT ON COLUMN sys_task.connection_name       IS '关联连接名称(冗余)';
COMMENT ON COLUMN sys_task.schema_name           IS '目标 Schema';
COMMENT ON COLUMN sys_task.target_label          IS '目标 VertexLabel / EdgeLabel';
COMMENT ON COLUMN sys_task.params                IS '执行参数 JSON';
COMMENT ON COLUMN sys_task.result_summary        IS '结果摘要 JSON';
COMMENT ON COLUMN sys_task.error_message         IS '错误信息';
COMMENT ON COLUMN sys_task.progress_percent      IS '进度百分比 0-100';
COMMENT ON COLUMN sys_task.processed_count       IS '已处理数量';
COMMENT ON COLUMN sys_task.success_count         IS '成功数量';
COMMENT ON COLUMN sys_task.failed_count          IS '失败数量';
COMMENT ON COLUMN sys_task.total_count           IS '总数量(可空)';
COMMENT ON COLUMN sys_task.current_stage         IS '当前阶段描述';
COMMENT ON COLUMN sys_task.result_file_path      IS '结果文件相对路径';
COMMENT ON COLUMN sys_task.error_file_path       IS '错误数据文件相对路径';
COMMENT ON COLUMN sys_task.parent_task_id        IS '父任务ID(重试场景)';
COMMENT ON COLUMN sys_task.user_id               IS '创建人ID';
COMMENT ON COLUMN sys_task.username              IS '创建人用户名';
COMMENT ON COLUMN sys_task.create_time           IS '创建时间';
COMMENT ON COLUMN sys_task.start_time            IS '开始执行时间';
COMMENT ON COLUMN sys_task.end_time              IS '结束时间';
COMMENT ON COLUMN sys_task.last_heartbeat_time   IS '最近心跳时间';
