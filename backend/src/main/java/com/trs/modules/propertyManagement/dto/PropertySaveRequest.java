package com.trs.modules.propertyManagement.dto;

/**
 * 新增属性请求体。
 *
 * @author czh
 * @date 2026/07/08
 */
public class PropertySaveRequest {

    private String name;
    private String propertyType;
    private String displayName;
    private boolean listDisplay;
    private boolean searchable;
    private boolean createIndex;
    private String indexType;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isListDisplay() { return listDisplay; }
    public void setListDisplay(boolean listDisplay) { this.listDisplay = listDisplay; }
    public boolean isSearchable() { return searchable; }
    public void setSearchable(boolean searchable) { this.searchable = searchable; }
    public boolean isCreateIndex() { return createIndex; }
    public void setCreateIndex(boolean createIndex) { this.createIndex = createIndex; }
    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
