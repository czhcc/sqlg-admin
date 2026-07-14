package com.trs.modules.log.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体。
 *
 * @author czh
 * @date 2026/07/13
 */
@Data
public class OperationLog {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String method;
    private String params;
    private String result;
    private String ip;
    private Integer costMs;
    private LocalDateTime createTime;

    private String operationType;
    private String operationName;
    private String status;
    private Long connectionId;
    private String connectionName;
    private String jdbcUrlMasked;
    private String schemaName;
    private String objectType;
    private String objectName;
    private String objectId;
    private String detail;
    private Integer affectedCount;
    private String errorMessage;
    private String userAgent;
    private Boolean isDangerous;
}
