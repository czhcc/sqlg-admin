package com.trs.modules.task.engine;

/**
 * 进度上报器,handler 通过它在执行过程中更新进度计数和阶段描述。
 *
 * @author czh
 * @date 2026/07/23
 */
public interface ProgressReporter {

    /**
     * 上报进度。
     *
     * @param progressPercent 0-100
     * @param processedCount  已处理数量
     * @param successCount    成功数量
     * @param failedCount     失败数量
     * @param totalCount      总数量(可空)
     * @param currentStage    当前阶段描述
     */
    void report(int progressPercent, long processedCount, long successCount,
                long failedCount, Long totalCount, String currentStage);
}
