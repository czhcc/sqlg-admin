package com.trs.modules.vertexType.dto;

/**
 * 底层表列信息。
 *
 * @author czh
 * @date 2026/07/07
 */
public class TableColumnInfo {

    private String columnName;
    private String dataType;
    private boolean nullable;
    private String defaultValue;

    public TableColumnInfo() {}

    public TableColumnInfo(String columnName, String dataType, boolean nullable, String defaultValue) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
    }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
