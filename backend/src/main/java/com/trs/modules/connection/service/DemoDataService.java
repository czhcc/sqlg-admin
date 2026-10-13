package com.trs.modules.connection.service;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.SqlgGraph;

/**
 * 在指定连接中创建 TinkerPop Modern 演示图，拒绝覆盖已有同名类型。
 *
 * @author codexsandboxoffline
 * @date 2026/1013
 */
@Service
public class DemoDataService {

    private final SqlgGraphRegistry registry;
    private final ConnectionVisibilityHelper visibilityHelper;
    private final OperationLogService logService;

    /**
     * 创建演示数据服务。
     *
     * @param registry 图连接注册器
     * @param visibilityHelper 当前用户连接可见性工具
     * @param logService 操作日志服务
     */
    public DemoDataService(SqlgGraphRegistry registry, ConnectionVisibilityHelper visibilityHelper,
                           OperationLogService logService) {
        this.registry = registry;
        this.visibilityHelper = visibilityHelper;
        this.logService = logService;
    }

    /**
     * 创建 Modern 的六个点和六条边，全部成功后提交图事务。
     *
     * @param connectionId 目标连接 ID
     */
    public void create(Long connectionId) {
        boolean visible = visibilityHelper.listEnabledForCurrentUser().stream()
                .anyMatch(connection -> connection.getId().equals(connectionId));
        if (!visible) {
            throw new IllegalArgumentException("连接不存在、已停用或不可见");
        }
        SqlgGraph graph = registry.get(connectionId);
        // 同一缓存连接上的演示创建串行执行，重复点击不会插入第二套数据。
        synchronized (graph) {
            graph.tx().rollback();
            try {
                var schema = graph.getTopology().getSchema("public");
                if (schema.isPresent() && (schema.get().getVertexLabel("person").isPresent()
                        || schema.get().getVertexLabel("software").isPresent()
                        || schema.get().getEdgeLabel("knows").isPresent()
                        || schema.get().getEdgeLabel("created").isPresent())) {
                    throw new IllegalArgumentException(
                            "演示数据已存在或类型名称冲突：public 中的 person、software、knows、created 必须不存在");
                }

                // 保留官方名称、属性类型及边方向，主键由 Sqlg 生成。
                Vertex marko = graph.addVertex(T.label, "public.person", "name", "marko", "age", 29);
                Vertex vadas = graph.addVertex(T.label, "public.person", "name", "vadas", "age", 27);
                Vertex lop = graph.addVertex(T.label, "public.software", "name", "lop", "lang", "java");
                Vertex josh = graph.addVertex(T.label, "public.person", "name", "josh", "age", 32);
                Vertex ripple = graph.addVertex(T.label, "public.software", "name", "ripple", "lang", "java");
                Vertex peter = graph.addVertex(T.label, "public.person", "name", "peter", "age", 35);
                marko.addEdge("knows", vadas, "weight", 0.5d);
                marko.addEdge("knows", josh, "weight", 1.0d);
                marko.addEdge("created", lop, "weight", 0.4d);
                josh.addEdge("created", ripple, "weight", 1.0d);
                josh.addEdge("created", lop, "weight", 0.4d);
                peter.addEdge("created", lop, "weight", 0.2d);
                graph.tx().commit();
            } catch (RuntimeException e) {
                graph.tx().rollback();
                audit(connectionId, "FAILED", e.getMessage(), 0);
                throw e;
            }
        }
        audit(connectionId, "SUCCESS", "已创建 6 个点和 6 条边", 12);
    }

    private void audit(Long connectionId, String status, String result, int affected) {
        logService.log().module("连接管理").action("创建演示数据").httpMethod("POST")
                .type("CREATE").name("创建 TinkerPop Modern 演示数据")
                .status(status).connection(connectionId).schema("public")
                .objectType("Graph").objectName("TinkerPop Modern")
                .affected(affected).result(result).submit();
    }
}
