package com.trs.modules.topology.dto;

import java.util.List;

public class VertexLabelDto extends LabelDto {
    private List<EdgeBriefDto> inEdgeLabels;
    private List<EdgeBriefDto> outEdgeLabels;

    public List<EdgeBriefDto> getInEdgeLabels() { return inEdgeLabels; }
    public void setInEdgeLabels(List<EdgeBriefDto> inEdgeLabels) { this.inEdgeLabels = inEdgeLabels; }
    public List<EdgeBriefDto> getOutEdgeLabels() { return outEdgeLabels; }
    public void setOutEdgeLabels(List<EdgeBriefDto> outEdgeLabels) { this.outEdgeLabels = outEdgeLabels; }
}
