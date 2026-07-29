package com.trs.modules.diagnostic.service;

import com.trs.modules.task.engine.ProgressReporter;
import com.trs.modules.task.engine.TaskContext;
import org.umlg.sqlg.structure.SqlgGraph;

import java.util.List;

/**
 * 单条诊断检查的抽象。每个实现由 Spring 自动注册到 {@link DiagnosticCheckRegistry},
 * 由 DiagnosticTaskHandler 按用户选择的检查项列表迭代执行。
 * <p>
 * 实现约定:
 * <ul>
 *   <li>{@link #id()} 全局唯一,形如 "topology.missing_vertex_table"</li>
 *   <li>{@link #category()} 用于前端分组展示,取值:TOPOLOGY / LABEL_STATUS / INDEX / VERTEX_DATA / EDGE_DATA / CONNECTION</li>
 *   <li>{@link #isHeavy()} 为 true 的检查会跑全量扫描,只应在异步任务中执行</li>
 *   <li>{@link #execute(SqlgGraph, TaskContext, ProgressReporter)} 必须周期性调用 {@code ctx.throwIfCancelled()} 以支持取消</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0728
 */
public interface DiagnosticCheck {

    String id();

    String category();

    /** 人类可读的检查项名称(中文),用于前端展示。 */
    String displayName();

    /** 简短描述,用于前端 tooltip。 */
    String description();

    /** 是否为耗时检查(全量扫描、跨表 join 等)。 */
    boolean isHeavy();

    /**
     * 执行检查,返回 0 或多条问题。无问题返回空列表(非 null)。
     */
    List<DiagnosticIssue> execute(SqlgGraph graph, TaskContext ctx, ProgressReporter reporter) throws Exception;
}
