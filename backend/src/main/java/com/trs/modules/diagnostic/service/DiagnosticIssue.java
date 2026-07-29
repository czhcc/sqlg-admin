package com.trs.modules.diagnostic.service;

/**
 * 单条诊断问题,包含严重级别、定位信息、人类可读消息、建议处理方式、前端跳转目标。
 * <p>
 * 由各个 {@link DiagnosticCheck} 产出,最终由 DiagnosticTaskHandler 聚合成诊断报告。
 *
 * @param level       严重级别:INFO / WARNING / ERROR / CRITICAL
 * @param category    所属检查类别:TOPOLOGY / LABEL_STATUS / INDEX / VERTEX_DATA / EDGE_DATA / CONNECTION
 * @param code        问题编码,如 MISSING_VERTEX_TABLE,供前端按 code 做翻译/图标映射
 * @param target      问题定位,如 "public.Person" 或 "public.V_Person.name"
 * @param message     人类可读的问题描述
 * @param suggestion  建议处理方式
 * @param jumpTarget  前端路由跳转目标,如 "/vertex-type" 或 "/topology",可空
 * @author czh
 * @date 2026/0728
 */
public record DiagnosticIssue(
        String level,
        String category,
        String code,
        String target,
        String message,
        String suggestion,
        String jumpTarget
) {

    public static final String INFO = "INFO";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";
    public static final String CRITICAL = "CRITICAL";

    public static DiagnosticIssue of(String level, String category, String code,
                                      String target, String message, String suggestion, String jumpTarget) {
        return new DiagnosticIssue(level, category, code, target, message, suggestion, jumpTarget);
    }
}
