package com.trs.modules.task.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.modules.edgeType.service.EdgeTypeService;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import com.trs.modules.task.entity.Task;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.vertexType.service.VertexTypeService;
import org.springframework.stereotype.Component;

/**
 * 数据维护任务处理器,覆盖批量删除点/边和清空点/边四种维护操作。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class MaintenanceTaskHandler extends AbstractTaskHandler {

    private final VertexTypeService vertexTypeService;
    private final EdgeTypeService edgeTypeService;

    public MaintenanceTaskHandler(SqlgGraphRegistry registry,
                                    VertexTypeService vertexTypeService,
                                    EdgeTypeService edgeTypeService) {
        super(registry);
        this.vertexTypeService = vertexTypeService;
        this.edgeTypeService = edgeTypeService;
    }

    @Override
    public String taskType() {
        return "MAINTENANCE";
    }

    @Override
    public String buildDisplayName(Task task) {
        return task.getName();
    }

    @Override
    public String execute(TaskContext ctx, ProgressReporter reporter) throws Exception {
        JsonNode params = parseParams(ctx);
        String type = str(params, "subType");
        Long connId = ctx.getConnectionId();
        String schema = str(params, "schema");
        String label = str(params, "label");
        ObjectNode result = result();

        report(reporter, 10, 0, 0, 0, null, "开始执行");

        switch (type) {
            case "CLEAR_VERTEX" -> {
                long count = vertexTypeService.clearVertices(connId, schema, label);
                report(reporter, 100, count, count, 0, count, "完成");
                result.put("successCount", count);
                result.put("processedCount", count);
            }
            case "CLEAR_EDGE" -> {
                long count = edgeTypeService.clearEdges(connId, schema, label);
                report(reporter, 100, count, count, 0, count, "完成");
                result.put("successCount", count);
                result.put("processedCount", count);
            }
            case "BATCH_DELETE_VERTEX" -> {
                if (!params.has("vertexIds") || !params.get("vertexIds").isArray()) {
                    throw new IllegalArgumentException("缺少 vertexIds 数组参数");
                }
                var graph = graph(ctx);
                int deleted = 0;
                int failed = 0;
                JsonNode ids = params.get("vertexIds");
                for (JsonNode idNode : ids) {
                    ctx.throwIfCancelled();
                    try {
                        Object id = parseVertexId(graph, idNode.asText());
                        graph.traversal().V(id).drop().iterate();
                        graph.tx().commit();
                        deleted++;
                        if (deleted % 50 == 0) {
                            report(reporter, 10 + (int) (80.0 * deleted / ids.size()),
                                    deleted, deleted, failed, (long) ids.size(), "已删除 " + deleted);
                        }
                    } catch (Exception e) {
                        graph.tx().rollback();
                        failed++;
                    }
                }
                report(reporter, 100, deleted + failed, deleted, failed, (long) ids.size(), "完成");
                result.put("successCount", deleted);
                result.put("failedCount", failed);
                result.put("processedCount", deleted + failed);
            }
            case "BATCH_DELETE_EDGE" -> {
                if (!params.has("edgeIds") || !params.get("edgeIds").isArray()) {
                    throw new IllegalArgumentException("缺少 edgeIds 数组参数");
                }
                var graph = graph(ctx);
                int deleted = 0;
                int failed = 0;
                JsonNode ids = params.get("edgeIds");
                for (JsonNode idNode : ids) {
                    ctx.throwIfCancelled();
                    try {
                        Object id = parseEdgeId(graph, idNode.asText());
                        graph.traversal().E(id).drop().iterate();
                        graph.tx().commit();
                        deleted++;
                        if (deleted % 50 == 0) {
                            report(reporter, 10 + (int) (80.0 * deleted / ids.size()),
                                    deleted, deleted, failed, (long) ids.size(), "已删除 " + deleted);
                        }
                    } catch (Exception e) {
                        graph.tx().rollback();
                        failed++;
                    }
                }
                report(reporter, 100, deleted + failed, deleted, failed, (long) ids.size(), "完成");
                result.put("successCount", deleted);
                result.put("failedCount", failed);
                result.put("processedCount", deleted + failed);
            }
            default -> throw new IllegalArgumentException("未知的维护子类型: " + type);
        }

        return result.toString();
    }

    private Object parseVertexId(org.umlg.sqlg.structure.SqlgGraph graph, String idStr) {
        try {
            return org.umlg.sqlg.structure.RecordId.from(idStr);
        } catch (Exception e) {
            return Long.valueOf(idStr);
        }
    }

    private Object parseEdgeId(org.umlg.sqlg.structure.SqlgGraph graph, String idStr) {
        try {
            return org.umlg.sqlg.structure.RecordId.from(idStr);
        } catch (Exception e) {
            return Long.valueOf(idStr);
        }
    }
}
