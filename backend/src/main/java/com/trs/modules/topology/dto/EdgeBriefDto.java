package com.trs.modules.topology.dto;

public class EdgeBriefDto {
    private String label;
    private String schema;
    private String fullName;

    public EdgeBriefDto() {}

    public EdgeBriefDto(String label, String schema, String fullName) {
        this.label = label;
        this.schema = schema;
        this.fullName = fullName;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
