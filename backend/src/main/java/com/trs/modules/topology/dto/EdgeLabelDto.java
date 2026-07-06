package com.trs.modules.topology.dto;

import java.util.List;

public class EdgeLabelDto extends LabelDto {
    private List<String> outVertexLabels;
    private List<String> inVertexLabels;

    public List<String> getOutVertexLabels() { return outVertexLabels; }
    public void setOutVertexLabels(List<String> outVertexLabels) { this.outVertexLabels = outVertexLabels; }
    public List<String> getInVertexLabels() { return inVertexLabels; }
    public void setInVertexLabels(List<String> inVertexLabels) { this.inVertexLabels = inVertexLabels; }
}
