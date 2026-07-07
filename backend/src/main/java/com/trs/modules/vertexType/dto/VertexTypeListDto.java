package com.trs.modules.vertexType.dto;

import java.util.List;

/**
 * 点类型列表行 DTO,每个 VertexLabel 一行。
 *
 * @author czh
 * @date 2026/07/07
 */
public class VertexTypeListDto {

    /** Schema 名称,如 public */
    private String schema;

    /** VertexLabel 名称,如 Person */
    private String label;

    /** 完整名 schema.label,如 public.Person */
    private String fullName;

    /** 底层表名,如 public."V_Person" */
    private String tableName;

    /** 属性数量 */
    private int propertyCount;

    /** Identifier 字段列表 */
    private List<String> identifiers;

    /** 索引数量 */
    private int indexCount;

    /** 点数据量(近似) */
    private long vertexCount;

    /** 创建时间(best-effort,可能为 null) */
    private String createdTime;

    /** 状态:active / empty */
    private String status;

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public int getPropertyCount() { return propertyCount; }
    public void setPropertyCount(int propertyCount) { this.propertyCount = propertyCount; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
    public int getIndexCount() { return indexCount; }
    public void setIndexCount(int indexCount) { this.indexCount = indexCount; }
    public long getVertexCount() { return vertexCount; }
    public void setVertexCount(long vertexCount) { this.vertexCount = vertexCount; }
    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
