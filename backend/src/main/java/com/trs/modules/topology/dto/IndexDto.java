package com.trs.modules.topology.dto;

import java.util.List;

public class IndexDto {
    private String name;
    private String indexType;
    private List<String> properties;

    public IndexDto() {}

    public IndexDto(String name, String indexType, List<String> properties) {
        this.name = name;
        this.indexType = indexType;
        this.properties = properties;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public List<String> getProperties() { return properties; }
    public void setProperties(List<String> properties) { this.properties = properties; }
}
