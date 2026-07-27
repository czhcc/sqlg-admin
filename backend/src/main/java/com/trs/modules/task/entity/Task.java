package com.trs.modules.task.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步任务实体,记录任务中心所有任务的生命周期、进度和结果。
 *
 * @author czh
 * @date 2026/07/23
 */
@Data
public class Task {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String CAT_IMPORT = "IMPORT";
    public static final String CAT_EXPORT = "EXPORT";
    public static final String CAT_MAINTENANCE = "MAINTENANCE";
    public static final String CAT_STATISTICS = "STATISTICS";
    public static final String CAT_DIAGNOSTIC = "DIAGNOSTIC";

    private Long id;
    private String taskNo;
    private String taskType;
    private String category;
    private String name;
    private String status;
    private Long connectionId;
    private String connectionName;
    private String schemaName;
    private String targetLabel;
    private String params;
    private String resultSummary;
    private String errorMessage;
    private Integer progressPercent;
    private Long processedCount;
    private Long successCount;
    private Long failedCount;
    private Long totalCount;
    private String currentStage;
    private String resultFilePath;
    private String errorFilePath;
    private Long parentTaskId;
    private Long userId;
    private String username;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lastHeartbeatTime;
}
