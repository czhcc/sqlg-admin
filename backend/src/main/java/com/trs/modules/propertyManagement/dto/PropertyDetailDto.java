package com.trs.modules.propertyManagement.dto;

/**
 * 属性详情 DTO,合并 sqlg topology 信息与 sys_property_meta UI 元数据。
 *
 * @author czh
 * @date 2026/07/08
 */
public class PropertyDetailDto {

    private String name;
    private String propertyType;
    private String dbType;
    private boolean identifier;
    private boolean indexed;
    private String indexName;
    private String indexType;
    private String displayName;
    private boolean searchable;
    private boolean listDisplay;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public boolean isIdentifier() { return identifier; }
    public void setIdentifier(boolean identifier) { this.identifier = identifier; }
    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isSearchable() { return searchable; }
    public void setSearchable(boolean searchable) { this.searchable = searchable; }
    public boolean isListDisplay() { return listDisplay; }
    public void setListDisplay(boolean listDisplay) { this.listDisplay = listDisplay; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
