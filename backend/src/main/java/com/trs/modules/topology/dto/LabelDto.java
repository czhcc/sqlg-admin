package com.trs.modules.topology.dto;

import java.util.List;

public class LabelDto {
    private String name;
    private String fullName;
    private List<PropertyDto> properties;
    private List<IndexDto> indexes;
    private List<String> identifiers;
    private boolean partitioned;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public List<PropertyDto> getProperties() { return properties; }
    public void setProperties(List<PropertyDto> properties) { this.properties = properties; }
    public List<IndexDto> getIndexes() { return indexes; }
    public void setIndexes(List<IndexDto> indexes) { this.indexes = indexes; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
    public boolean isPartitioned() { return partitioned; }
    public void setPartitioned(boolean partitioned) { this.partitioned = partitioned; }
}
