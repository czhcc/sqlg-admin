package com.trs.modules.task.engine;

import com.trs.modules.diagnostic.handlers.DiagnosticTaskHandler;
import com.trs.modules.task.handlers.ImportExportTaskHandler;
import com.trs.modules.task.handlers.MaintenanceTaskHandler;
import com.trs.modules.task.handlers.StatisticsDiagnosticTaskHandler;
import org.springframework.stereotype.Component;

/**
 * 任务类型 → 处理器映射注册表。
 * <p>
 * 将导入导出、维护、统计诊断和图算法任务路由到对应处理器。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class TaskHandlerRegistry {

    private final ImportExportTaskHandler importExportHandler;
    private final MaintenanceTaskHandler maintenanceHandler;
    private final StatisticsDiagnosticTaskHandler statsDiagHandler;
    private final DiagnosticTaskHandler diagnosticHandler;
    private final com.trs.modules.graphAlgorithm.GraphAlgorithmTaskHandler algorithmHandler;

    /** 注入各类任务处理器。 */
    public TaskHandlerRegistry(ImportExportTaskHandler importExportHandler,
                                 MaintenanceTaskHandler maintenanceHandler,
                                 StatisticsDiagnosticTaskHandler statsDiagHandler,
                                 DiagnosticTaskHandler diagnosticHandler,
                                 com.trs.modules.graphAlgorithm.GraphAlgorithmTaskHandler algorithmHandler) {
        this.importExportHandler = importExportHandler;
        this.maintenanceHandler = maintenanceHandler;
        this.statsDiagHandler = statsDiagHandler;
        this.diagnosticHandler = diagnosticHandler;
        this.algorithmHandler = algorithmHandler;
    }

    /** 根据具体任务类型选择处理器。 */
    public TaskHandler getHandler(String taskType) {
        return switch (taskType) {
            case "GRAPH_ALGORITHM" -> algorithmHandler;
            case "IMPORT_VERTEX", "IMPORT_EDGE", "IMPORT_TOPOLOGY",
                 "EXPORT_VERTEX", "EXPORT_EDGE", "EXPORT_TOPOLOGY" -> importExportHandler;
            case "BATCH_DELETE_VERTEX", "BATCH_DELETE_EDGE",
                 "CLEAR_VERTEX", "CLEAR_EDGE" -> maintenanceHandler;
            case "STAT_VERTEX_COUNTS", "STAT_EDGE_COUNTS", "STAT_ORPHAN",
                 "DIAG_TOPOLOGY", "DIAG_INDEX", "DIAG_DATA_INTEGRITY", "DIAG_ORPHAN" -> statsDiagHandler;
            case "DIAG_CUSTOM" -> diagnosticHandler;
            default -> null;
        };
    }
}
