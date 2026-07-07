package com.trs.modules.edgeType.dto;

import java.util.List;

/**
 * 边类型列表行 DTO,每个 EdgeLabel 一行。
 *
 * @author czh
 * @date 2026/07/07
 */
public class EdgeTypeListDto {

    /** Schema 名称,即出点类型所在的 schema,如 public */
    private String schema;

    /** EdgeLabel 名称,如 knows */
    private String label;

    /** 完整名 schema.label,如 public.knows */
    private String fullName;

    /** 底层表名,如 public."E_knows" */
    private String tableName;

    /** 出点类型全名列表(schema.label),如 ["public.Person"] */
    private List<String> outVertexLabels;

    /** 入点类型全名列表(schema.label),如 ["public.Person","public.Company"] */
    private List<String> inVertexLabels;

    /** 属性数量 */
    private int propertyCount;

    /** Identifier 字段列表 */
    private List<String> identifiers;

    /** 索引数量 */
    private int indexCount;

    /** 边数据量 */
    private long edgeCount;

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public List<String> getOutVertexLabels() { return outVertexLabels; }
    public void setOutVertexLabels(List<String> outVertexLabels) { this.outVertexLabels = outVertexLabels; }
    public List<String> getInVertexLabels() { return inVertexLabels; }
    public void setInVertexLabels(List<String> inVertexLabels) { this.inVertexLabels = inVertexLabels; }
    public int getPropertyCount() { return propertyCount; }
    public void setPropertyCount(int propertyCount) { this.propertyCount = propertyCount; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
    public int getIndexCount() { return indexCount; }
    public void setIndexCount(int indexCount) { this.indexCount = indexCount; }
    public long getEdgeCount() { return edgeCount; }
    public void setEdgeCount(long edgeCount) { this.edgeCount = edgeCount; }
}
