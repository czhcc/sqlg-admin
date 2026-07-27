package com.trs.modules.task.engine;

import com.trs.modules.task.handlers.ImportExportTaskHandler;
import com.trs.modules.task.handlers.MaintenanceTaskHandler;
import com.trs.modules.task.handlers.StatisticsDiagnosticTaskHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务类型 → 处理器映射注册表。
 * <p>
 * TaskService 创建 17 种具体类型(IMPORT_VERTEX/EXPORT_EDGE/...)的任务,
 * 而处理器只有 3 个聚合实现,本类负责将具体类型路由到对应处理器。
 *
 * @author czh
 * @date 2026/07/23
 */
@Component
public class TaskHandlerRegistry {

    private final ImportExportTaskHandler importExportHandler;
    private final MaintenanceTaskHandler maintenanceHandler;
    private final StatisticsDiagnosticTaskHandler statsDiagHandler;

    public TaskHandlerRegistry(ImportExportTaskHandler importExportHandler,
                                 MaintenanceTaskHandler maintenanceHandler,
                                 StatisticsDiagnosticTaskHandler statsDiagHandler) {
        this.importExportHandler = importExportHandler;
        this.maintenanceHandler = maintenanceHandler;
        this.statsDiagHandler = statsDiagHandler;
    }

    public TaskHandler getHandler(String taskType) {
        return switch (taskType) {
            case "IMPORT_VERTEX", "IMPORT_EDGE", "IMPORT_TOPOLOGY",
                 "EXPORT_VERTEX", "EXPORT_EDGE", "EXPORT_TOPOLOGY" -> importExportHandler;
            case "BATCH_DELETE_VERTEX", "BATCH_DELETE_EDGE",
                 "CLEAR_VERTEX", "CLEAR_EDGE" -> maintenanceHandler;
            case "STAT_VERTEX_COUNTS", "STAT_EDGE_COUNTS", "STAT_ORPHAN",
                 "DIAG_TOPOLOGY", "DIAG_INDEX", "DIAG_DATA_INTEGRITY", "DIAG_ORPHAN" -> statsDiagHandler;
            default -> null;
        };
    }
}
