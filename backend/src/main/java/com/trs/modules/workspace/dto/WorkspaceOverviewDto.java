package com.trs.modules.workspace.dto;

import lombok.Data;

import java.util.List;

/**
 * 工作台概览 DTO,聚合当前连接信息、Topology 统计和数据规模统计。
 *
 * @author czh
 * @date 2026/07/23
 */
@Data
public class WorkspaceOverviewDto {

    /** 当前连接信息 */
    private ConnectionInfo connection;

    /** Topology 概览统计 */
    private TopologyStats topology;

    /** 图数据规模统计 */
    private DataStats dataStats;

    /** 数据量 Top N 的 VertexLabel,用于前端柱状图 */
    private List<LabelCountItem> topVertexLabels;

    /** 数据量 Top N 的 EdgeLabel,用于前端柱状图 */
    private List<LabelCountItem> topEdgeLabels;

    /**
     * 当前连接基本信息。
     */
    @Data
    public static class ConnectionInfo {
        private Long id;
        private String name;
        private String dbType;
        private String jdbcUrl;
        private String username;
        private String status;
        private String statusMessage;
        private String environment;
        private int schemaCount;
        private long responseTimeMs;
        private String lastCheckTime;
    }

    /**
     * Topology 统计指标。
     */
    @Data
    public static class TopologyStats {
        private int schemaCount;
        private int vertexLabelCount;
        private int edgeLabelCount;
        private int vertexPropertyCount;
        private int edgePropertyCount;
        private int indexCount;
        private int identifierCount;
        private int emptyVertexLabelCount;
        private int emptyEdgeLabelCount;
    }

    /**
     * 图数据规模统计。
     */
    @Data
    public static class DataStats {
        private long totalVertices;
        private long totalEdges;
        private LabelCountItem largestVertexLabel;
        private LabelCountItem largestEdgeLabel;
        private long orphanVertexCount;
        private int emptyVertexLabelCount;
        private int emptyEdgeLabelCount;
    }

    /**
     * Label 及其数据量,用于排序展示。
     */
    @Data
    public static class LabelCountItem {
        private String schema;
        private String label;
        private String fullName;
        private long count;

        public LabelCountItem() {}

        public LabelCountItem(String schema, String label, long count) {
            this.schema = schema;
            this.label = label;
            this.fullName = schema + "." + label;
            this.count = count;
        }
    }
}
