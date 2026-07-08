-- =============================================================================
-- V3__property_meta.sql
-- 属性 UI 元数据 — 存储图库属性的显示名称、是否可搜索、是否列表展示等 UI 信息。
-- sqlg 的 topology 不包含这些 UI 概念,因此单独在管理库中维护。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_property_meta (
    id             BIGSERIAL      PRIMARY KEY,
    connection_id  BIGINT         NOT NULL,
    label_kind     VARCHAR(10)    NOT NULL,
    schema_name    VARCHAR(200)   NOT NULL,
    label_name     VARCHAR(200)   NOT NULL,
    property_name  VARCHAR(200)   NOT NULL,
    display_name   VARCHAR(200),
    is_searchable  SMALLINT       NOT NULL DEFAULT 0,
    is_list_display SMALLINT      NOT NULL DEFAULT 0,
    remark         TEXT,
    create_time    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prop_meta UNIQUE (connection_id, label_kind, schema_name, label_name, property_name)
);

COMMENT ON TABLE  sys_property_meta IS '图库属性 UI 元数据表';
COMMENT ON COLUMN sys_property_meta.id              IS '主键ID, 自增';
COMMENT ON COLUMN sys_property_meta.connection_id   IS '图数据库连接ID';
COMMENT ON COLUMN sys_property_meta.label_kind      IS 'Label 类型: vertex / edge';
COMMENT ON COLUMN sys_property_meta.schema_name     IS 'Sqlg schema 名称';
COMMENT ON COLUMN sys_property_meta.label_name      IS 'VertexLabel 或 EdgeLabel 名称';
COMMENT ON COLUMN sys_property_meta.property_name   IS '属性名';
COMMENT ON COLUMN sys_property_meta.display_name    IS '显示名称 (UI 用)';
COMMENT ON COLUMN sys_property_meta.is_searchable   IS '是否可搜索: 1=是 0=否';
COMMENT ON COLUMN sys_property_meta.is_list_display IS '是否列表展示: 1=是 0=否';
COMMENT ON COLUMN sys_property_meta.remark          IS '备注';
COMMENT ON COLUMN sys_property_meta.create_time     IS '创建时间';
COMMENT ON COLUMN sys_property_meta.update_time     IS '更新时间';
