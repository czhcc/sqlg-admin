package com.trs.modules.edgeType.dto;

import java.util.List;

/**
 * 新增边类型请求体。
 *
 * <p>出/入点类型必须位于同一 schema,边表也将创建在该 schema 下
 * (sqlg 约定:边表跟随出点的 schema)。
 *
 * @author czh
 * @date 2026/07/07
 */
public class EdgeTypeSaveRequest {

    /** schema 名称(出/入点同属,边表也在此 schema) */
    private String schema;

    /** 出点 VertexLabel 名称 */
    private String outLabel;

    /** 入点 VertexLabel 名称 */
    private String inLabel;

    /** EdgeLabel 名称 */
    private String label;

    /** identifier 属性名列表(选择字符串 ID 时必填一项) */
    private List<String> identifiers;

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getOutLabel() { return outLabel; }
    public void setOutLabel(String outLabel) { this.outLabel = outLabel; }
    public String getInLabel() { return inLabel; }
    public void setInLabel(String inLabel) { this.inLabel = inLabel; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
}
