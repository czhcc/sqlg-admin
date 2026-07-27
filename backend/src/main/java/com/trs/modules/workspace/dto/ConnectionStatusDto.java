package com.trs.modules.workspace.dto;

import lombok.Data;

/**
 * 连接状态概览项,用于工作台连接状态表格展示。
 *
 * @author czh
 * @date 2026/07/23
 */
@Data
public class ConnectionStatusDto {

    private Long id;
    private String name;
    private String dbType;
    private String environment;
    private String status;
    private Long responseTimeMs;
    private String lastCheckTime;
    private String errorMessage;

    /**
     * 构建正常状态的连接。
     */
    public static ConnectionStatusDto ok(Long id, String name, String dbType, String environment,
                                          long responseTimeMs, String lastCheckTime) {
        ConnectionStatusDto dto = new ConnectionStatusDto();
        dto.id = id;
        dto.name = name;
        dto.dbType = dbType;
        dto.environment = environment;
        dto.status = "ok";
        dto.responseTimeMs = responseTimeMs;
        dto.lastCheckTime = lastCheckTime;
        return dto;
    }

    /**
     * 构建异常状态的连接。
     */
    public static ConnectionStatusDto fail(Long id, String name, String dbType, String environment,
                                            String errorMessage, String lastCheckTime) {
        ConnectionStatusDto dto = new ConnectionStatusDto();
        dto.id = id;
        dto.name = name;
        dto.dbType = dbType;
        dto.environment = environment;
        dto.status = "error";
        dto.responseTimeMs = null;
        dto.lastCheckTime = lastCheckTime;
        dto.errorMessage = errorMessage;
        return dto;
    }
}
