package com.trs.modules.workspace.dto;

import lombok.Data;

/**
 * 工作台异常与风险提醒项。
 *
 * @author czh
 * @date 2026/07/23
 */
@Data
public class AlertItemDto {

    /** 级别: error / warning / info */
    private String level;

    /** 分类: connection / topology / data / import / gremlin / operation */
    private String category;

    /** 提醒标题 */
    private String title;

    /** 详细描述 */
    private String message;

    /** 跳转路径(前端路由),可选 */
    private String action;

    public AlertItemDto() {}

    /**
     * 构建一条异常/风险提醒。
     *
     * @param level    级别
     * @param category 分类
     * @param title    标题
     * @param message  描述
     * @param action   跳转路径
     */
    public AlertItemDto(String level, String category, String title, String message, String action) {
        this.level = level;
        this.category = category;
        this.title = title;
        this.message = message;
        this.action = action;
    }
}
