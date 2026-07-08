package com.trs.modules.propertyManagement.dto;

/**
 * 编辑属性请求体(属性类型不可改,属性名可改)。
 *
 * @author czh
 * @date 2026/07/08
 */
public class PropertyUpdateRequest {

    private String name;
    private String displayName;
    private boolean listDisplay;
    private boolean searchable;
    private boolean createIndex;
    private String indexType;
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
