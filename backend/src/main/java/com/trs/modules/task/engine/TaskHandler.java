package com.trs.modules.task.engine;

import com.trs.modules.task.entity.Task;

/**
 * 任务处理器接口,每种任务类型对应一个实现。
 * <p>
 * Engine 会在独立线程中调用 {@link #execute(TaskContext, ProgressReporter)},
 * handler 内部通过 context 检查取消标志、通过 reporter 上报进度。
 *
 * @author czh
 * @date 2026/07/23
 */
public interface TaskHandler {

    /**
     * 返回该 handler 处理的任务类型编码,对应 {@link Task#getTaskType()}。
     */
    String taskType();

    /**
     * 执行任务,返回结果摘要 JSON 字符串。
     *
     * @param ctx     任务上下文(含参数、存储路径、取消标志)
     * @param reporter 进度上报器
     * @return 结果摘要 JSON(将写入 task.result_summary)
     */
    String execute(TaskContext ctx, ProgressReporter reporter) throws Exception;

    /**
     * 基于任务参数生成人类可读的任务名称。
     */
    String buildDisplayName(Task task);
}
