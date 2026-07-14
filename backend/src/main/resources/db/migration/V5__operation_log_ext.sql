-- =============================================================================
-- V5__operation_log_ext.sql
-- 扩展操作日志表: 增加连接信息、操作对象、状态、危险标记等字段
-- =============================================================================

ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS operation_type   VARCHAR(32);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS operation_name   VARCHAR(256);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS status           VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS connection_id    BIGINT;
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS connection_name  VARCHAR(128);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS jdbc_url_masked  VARCHAR(512);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS schema_name      VARCHAR(128);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS object_type      VARCHAR(64);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS object_name      VARCHAR(512);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS object_id        VARCHAR(512);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS detail           TEXT;
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS affected_count   INTEGER;
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS error_message    TEXT;
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS user_agent       VARCHAR(512);
ALTER TABLE sys_operation_log ADD COLUMN IF NOT EXISTS is_dangerous     BOOLEAN      NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_oplog_time     ON sys_operation_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_oplog_user     ON sys_operation_log (user_id);
CREATE INDEX IF NOT EXISTS idx_oplog_module   ON sys_operation_log (module);
CREATE INDEX IF NOT EXISTS idx_oplog_conn     ON sys_operation_log (connection_id);
CREATE INDEX IF NOT EXISTS idx_oplog_status   ON sys_operation_log (status);
CREATE INDEX IF NOT EXISTS idx_oplog_danger   ON sys_operation_log (is_dangerous) WHERE is_dangerous = TRUE;

COMMENT ON COLUMN sys_operation_log.operation_type  IS '操作类型: CREATE/UPDATE/DELETE/RENAME/QUERY/IMPORT/EXPORT/CLEAR/EXECUTE/BLOCKED/LOGIN/LOGOUT';
COMMENT ON COLUMN sys_operation_log.operation_name  IS '操作名称,如 创建VertexLabel、删除点';
COMMENT ON COLUMN sys_operation_log.status          IS '操作结果: SUCCESS/FAILED/BLOCKED/PARTIAL_SUCCESS';
COMMENT ON COLUMN sys_operation_log.connection_id   IS '图数据库连接ID';
COMMENT ON COLUMN sys_operation_log.connection_name IS '图数据库连接名称';
COMMENT ON COLUMN sys_operation_log.jdbc_url_masked IS '脱敏后的 JDBC URL';
COMMENT ON COLUMN sys_operation_log.schema_name     IS 'Schema 名称';
COMMENT ON COLUMN sys_operation_log.object_type     IS '操作对象类型: Schema/VertexLabel/EdgeLabel/Property/Index/Vertex/Edge';
COMMENT ON COLUMN sys_operation_log.object_name     IS '操作对象名称';
COMMENT ON COLUMN sys_operation_log.object_id       IS '数据ID或业务标识';
COMMENT ON COLUMN sys_operation_log.detail          IS '操作详情 JSON';
COMMENT ON COLUMN sys_operation_log.affected_count  IS '影响数量';
COMMENT ON COLUMN sys_operation_log.error_message   IS '错误信息';
COMMENT ON COLUMN sys_operation_log.user_agent      IS '浏览器 User-Agent';
COMMENT ON COLUMN sys_operation_log.is_dangerous    IS '是否危险操作';
