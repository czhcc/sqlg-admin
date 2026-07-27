package com.trs.modules.task.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.modules.io.service.IoService;
import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import com.trs.modules.task.entity.Task;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据导入导出任务处理器,覆盖 IMPORT_VERTEX/IMPORT_EDGE/IMPORT_TOPOLOGY/
 * EXPORT_VERTEX/EXPORT_EDGE/EXPORT_TOPOLOGY 六种任务类型。
 * <p>
 * 第一版采用粗粒度进度(启动 5% → 执行 50% → 文件写入 95% → 完成 100%),
 * 复用 IoService 同步逻辑在异步线程中执行。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class ImportExportTaskHandler extends AbstractTaskHandler {

    private final IoService ioService;

    public ImportExportTaskHandler(SqlgGraphRegistry registry, IoService ioService) {
        super(registry);
        this.ioService = ioService;
    }

    @Override
    public String taskType() {
        return "IMPORT_EXPORT";
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
        ObjectNode result = result();

        report(reporter, 5, 0, 0, 0, null, "初始化");

        switch (type) {
            case "IMPORT_VERTEX" -> {
                IoService.ImportVerticesRequest req = parseVertexImportRequest(params);
                report(reporter, 10, 0, 0, 0, null, "解析导入数据");
                Map<String, Object> r = ioService.importVertices(connId, req);
                report(reporter, 90, getLong(r, "totalRows"), getLong(r, "imported") + getInt(r, "updated"), getInt(r, "errors"), null, "导入完成");
                result.put("successCount", getLong(r, "imported") + getInt(r, "updated"));
                result.put("failedCount", getInt(r, "errors"));
                result.put("processedCount", getLong(r, "totalRows"));
                result.putPOJO("details", MAPPER.valueToTree(r));
            }
            case "IMPORT_EDGE" -> {
                IoService.ImportEdgesRequest req = parseEdgeImportRequest(params);
                report(reporter, 10, 0, 0, 0, null, "解析导入数据");
                Map<String, Object> r = ioService.importEdges(connId, req);
                report(reporter, 90, getLong(r, "totalRows"), getInt(r, "imported"), getInt(r, "errors"), null, "导入完成");
                result.put("successCount", getInt(r, "imported"));
                result.put("failedCount", getInt(r, "errors"));
                result.put("processedCount", getLong(r, "totalRows"));
                result.putPOJO("details", MAPPER.valueToTree(r));
            }
            case "IMPORT_TOPOLOGY" -> {
                String content = str(params, "content");
                report(reporter, 10, 0, 0, 0, null, "解析 Topology JSON");
                Map<String, Object> r = ioService.importTopology(connId, content);
                report(reporter, 90, 0, getInt(r, "vertexLabelsCreated") + getInt(r, "edgeLabelsCreated"),
                        getInt(r, "errorCount"), null, "Topology 导入完成");
                result.put("successCount", getInt(r, "vertexLabelsCreated") + getInt(r, "edgeLabelsCreated"));
                result.put("failedCount", getInt(r, "errorCount"));
                result.putPOJO("details", MAPPER.valueToTree(r));
            }
            case "EXPORT_VERTEX" -> {
                String schema = str(params, "schema");
                String label = str(params, "label");
                String format = str(params, "format", "csv");
                report(reporter, 30, 0, 0, 0, null, "导出点数据: " + schema + "." + label);
                Map<String, Object> r = ioService.exportVertices(connId, schema, label, format);
                report(reporter, 80, 0, getInt(r, "rowCount"), 0, null, "写入文件");
                String filePath = writeResultFile(ctx, (String) r.get("filename"), (String) r.get("content"));
                result.put("successCount", getInt(r, "rowCount"));
                result.put("resultFilePath", filePath);
                result.put("fileName", (String) r.get("filename"));
            }
            case "EXPORT_EDGE" -> {
                String schema = str(params, "schema");
                String label = str(params, "label");
                String format = str(params, "format", "csv");
                report(reporter, 30, 0, 0, 0, null, "导出边数据: " + schema + "." + label);
                Map<String, Object> r = ioService.exportEdges(connId, schema, label, format);
                report(reporter, 80, 0, getInt(r, "rowCount"), 0, null, "写入文件");
                String filePath = writeResultFile(ctx, (String) r.get("filename"), (String) r.get("content"));
                result.put("successCount", getInt(r, "rowCount"));
                result.put("resultFilePath", filePath);
                result.put("fileName", (String) r.get("filename"));
            }
            case "EXPORT_TOPOLOGY" -> {
                report(reporter, 30, 0, 0, 0, null, "导出 Topology");
                Map<String, Object> r = ioService.exportTopology(connId);
                report(reporter, 80, 0, getInt(r, "rowCount"), 0, null, "写入文件");
                String filePath = writeResultFile(ctx, (String) r.get("filename"), (String) r.get("content"));
                result.put("successCount", getInt(r, "rowCount"));
                result.put("resultFilePath", filePath);
                result.put("fileName", (String) r.get("filename"));
            }
            default -> throw new IllegalArgumentException("未知的导入导出子类型: " + type);
        }

        report(reporter, 100, result.path("processedCount").asLong(),
                result.path("successCount").asLong(),
                result.path("failedCount").asLong(), null, "完成");
        return result.toString();
    }

    private IoService.ImportVerticesRequest parseVertexImportRequest(JsonNode params) {
        IoService.ImportVerticesRequest req = new IoService.ImportVerticesRequest();
        req.setContent(str(params, "content"));
        req.setFormat(str(params, "format", "csv"));
        req.setSchema(str(params, "schema"));
        req.setLabel(str(params, "label"));
        req.setOverwrite(params.has("overwrite") && params.get("overwrite").asBoolean());
        if (params.has("fieldMapping")) {
            Map<String, String> mapping = new LinkedHashMap<>();
            JsonNode fm = params.get("fieldMapping");
            fm.fieldNames().forEachRemaining(k -> mapping.put(k, fm.get(k).asText()));
            req.setFieldMapping(mapping);
        }
        return req;
    }

    private IoService.ImportEdgesRequest parseEdgeImportRequest(JsonNode params) {
        IoService.ImportEdgesRequest req = new IoService.ImportEdgesRequest();
        req.setContent(str(params, "content"));
        req.setFormat(str(params, "format", "csv"));
        req.setSchema(str(params, "schema"));
        req.setLabel(str(params, "label"));
        req.setOutVertexLabel(str(params, "outVertexLabel"));
        req.setInVertexLabel(str(params, "inVertexLabel"));
        req.setOutVertexField(str(params, "outVertexField"));
        req.setInVertexField(str(params, "inVertexField"));
        if (params.has("fieldMapping")) {
            Map<String, String> mapping = new LinkedHashMap<>();
            JsonNode fm = params.get("fieldMapping");
            fm.fieldNames().forEachRemaining(k -> mapping.put(k, fm.get(k).asText()));
            req.setFieldMapping(mapping);
        }
        return req;
    }

    private long getLong(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.longValue();
        return 0;
    }

    private int getInt(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
