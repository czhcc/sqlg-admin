package com.trs.modules.task.engine;

/**
 * 异步任务执行上下文,由 TaskEngine 在提交任务时创建并传递给 TaskHandler。
 * <p>
 * Handler 通过本对象读取任务参数、检查取消标志、上报进度。
 *
 * @author czh
 * @date 2026/07/23
 */
public class TaskContext {

    private final Long taskId;
    private final String taskNo;
    private final Long connectionId;
    private final String params;
    private final String storageDir;
    private volatile boolean cancelRequested;

    public TaskContext(Long taskId, String taskNo, Long connectionId, String params, String storageDir) {
        this.taskId = taskId;
        this.taskNo = taskNo;
        this.connectionId = connectionId;
        this.params = params;
        this.storageDir = storageDir;
    }

    public Long getTaskId() { return taskId; }
    public String getTaskNo() { return taskNo; }
    public Long getConnectionId() { return connectionId; }
    public String getParams() { return params; }
    public String getStorageDir() { return storageDir; }

    public boolean isCancelRequested() { return cancelRequested; }

    public void requestCancel() { this.cancelRequested = true; }

    /**
     * 在批处理循环中调用,如果用户已请求取消则抛出异常终止执行。
     */
    public void throwIfCancelled() {
        if (cancelRequested) {
            throw new TaskCancelledException(taskNo);
        }
    }

    public static class TaskCancelledException extends RuntimeException {
        public TaskCancelledException(String taskNo) {
            super("任务已取消: " + taskNo);
        }
    }
}
