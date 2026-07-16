package com.trs.modules.edgeType;

import com.trs.common.Result;
import com.trs.modules.edgeType.dto.EdgeTypeDetailDto;
import com.trs.modules.edgeType.dto.EdgeTypeListDto;
import com.trs.modules.edgeType.dto.EdgeTypeSaveRequest;
import com.trs.modules.edgeType.service.EdgeTypeService;
import com.trs.modules.vertexType.dto.TableColumnInfo;
import com.trs.security.RequirePermission;
import com.trs.user.entity.User;
import com.trs.user.service.UserPreferenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 边类型管理 REST 控制器,提供 EdgeLabel 的列表、详情、新增、删除、
 * 清空边数据、底层表结构查看及 Gremlin/SQL 示例生成。
 *
 * @author czh
 * @date 2026/07/07
 */
@RestController
@RequestMapping("/edge-type")
public class EdgeTypeController {

    private final EdgeTypeService service;
    private final UserPreferenceService preferenceService;

    public EdgeTypeController(EdgeTypeService service, UserPreferenceService preferenceService) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    /**
     * 获取可用连接列表,含用户上次选择的连接 ID。
     *
     * @return {connections: [...], activeConnectionId: Long|null}
     */
    @GetMapping("/connections")
    public Result<Map<String, Object>> connections() {
        List<Map<String, Object>> conns = service.listConnections();
        Long remembered = null;
        User u = currentUser();
        if (u != null) {
            remembered = preferenceService.getAsLong(
                    u.getId(), UserPreferenceService.KEY_EDGE_TYPE_ACTIVE_CONNECTION);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", conns);
        result.put("activeConnectionId", remembered);
        return Result.ok(result);
    }

    /**
     * 记住用户选择的连接 ID。
     *
     * @param body {connectionId: Long|String|null}
     * @return 操作结果
     */
    @PutMapping("/active-connection")
    public Result<?> setActiveConnection(@RequestBody Map<String, Object> body) {
        User u = currentUser();
        if (u == null) return Result.fail(401, "未登录");
        Object v = body.get("connectionId");
        if (v == null || "".equals(v)) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_EDGE_TYPE_ACTIVE_CONNECTION, (Long) null);
        } else if (v instanceof Number n) {
            preferenceService.set(u.getId(),
                    UserPreferenceService.KEY_EDGE_TYPE_ACTIVE_CONNECTION, n.longValue());
        } else {
            try {
                preferenceService.set(u.getId(),
                        UserPreferenceService.KEY_EDGE_TYPE_ACTIVE_CONNECTION,
                        Long.valueOf(String.valueOf(v)));
            } catch (NumberFormatException e) {
                return Result.fail(400, "connectionId 必须是数字");
            }
        }
        return Result.ok();
    }

    /**
     * 列出指定连接下所有 EdgeLabel。
     *
     * @param connectionId 图数据库连接 ID
     * @return EdgeLabel 列表
     */
    @GetMapping("/{connectionId}")
    public Result<List<EdgeTypeListDto>> list(@PathVariable Long connectionId) {
        return Result.ok(service.list(connectionId));
    }

    /**
     * 获取单个 EdgeLabel 的详情。
     *
     * @param connectionId 图数据库连接 ID
     * @param schema       schema 名称
     * @param label        EdgeLabel 名称
     * @return 详情 DTO
     */
    @GetMapping("/{connectionId}/{schema}/{label}")
    public Result<EdgeTypeDetailDto> detail(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.getDetail(connectionId, schema, label));
    }

    /**
     * 新增 EdgeLabel。
     *
     * @param connectionId 图数据库连接 ID
     * @param req          请求体(需含 out/in 点类型 schema+label)
     * @return 操作结果
     */
    @RequirePermission(menu = "edge-type", code = "edge_type:create", name = "新增边类型")
    @PostMapping("/{connectionId}")
    public Result<?> create(
            @PathVariable Long connectionId,
            @RequestBody EdgeTypeSaveRequest req) {
        service.create(connectionId, req);
        return Result.ok();
    }

    /**
     * 删除 EdgeLabel — 同时删除底层 E_XXX 表和边数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param body         {schema: String, label: String}
     * @return 操作结果
     */
    @RequirePermission(menu = "edge-type", code = "edge_type:delete", name = "删除边类型")
    @DeleteMapping("/{connectionId}")
    public Result<?> delete(
            @PathVariable Long connectionId,
            @RequestBody Map<String, String> body) {
        String schema = body.get("schema");
        String label = body.get("label");
        if (schema == null || label == null) {
            return Result.fail(400, "schema 和 label 不能为空");
        }
        service.delete(connectionId, schema, label);
        return Result.ok();
    }

    /**
     * 清空边数据 — 只删边,保留 E_XXX 表和 EdgeLabel 定义。
     * 对应: g.E().hasLabel('X').drop().iterate()
     *
     * @param connectionId 图数据库连接 ID
     * @param body         {schema: String, label: String}
     * @return {deleted: 删除数量}
     */
    @RequirePermission(menu = "edge-type", code = "edge_data:clear", name = "清空边数据")
    @PostMapping("/{connectionId}/clear-edges")
    public Result<Map<String, Object>> clearEdges(
            @PathVariable Long connectionId,
            @RequestBody Map<String, String> body) {
        String schema = body.get("schema");
        String label = body.get("label");
        if (schema == null || label == null) {
            return Result.fail(400, "schema 和 label 不能为空");
        }
        long count = service.clearEdges(connectionId, schema, label);
        return Result.ok(Map.of("deleted", count));
    }

    /**
     * 查看底层物理表结构(information_schema.columns)。
     *
     * @param connectionId 图数据库连接 ID
     * @param schema       schema 名称
     * @param label        EdgeLabel 名称
     * @return 列信息列表
     */
    @GetMapping("/{connectionId}/{schema}/{label}/table-schema")
    public Result<List<TableColumnInfo>> tableSchema(
            @PathVariable Long connectionId,
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.getTableSchema(connectionId, schema, label));
    }

    /**
     * 列出指定连接下所有 VertexLabel,用于新增边类型表单的出/入点选择。
     *
     * @param connectionId 图数据库连接 ID
     * @return VertexLabel 列表(每项含 schema/label/fullName)
     */
    @GetMapping("/{connectionId}/vertex-labels")
    public Result<List<Map<String, Object>>> vertexLabels(@PathVariable Long connectionId) {
        return Result.ok(service.listVertexLabels(connectionId));
    }

    /**
     * 生成 Gremlin 示例查询。
     *
     * @param schema schema 名称
     * @param label  EdgeLabel 名称
     * @return 示例列表 [{title, code}]
     */
    @GetMapping("/gremlin-examples/{schema}/{label}")
    public Result<List<Map<String, String>>> gremlinExamples(
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.generateGremlinExamples(schema, label));
    }

    /**
     * 生成 SQL 示例查询。
     *
     * @param schema schema 名称
     * @param label  EdgeLabel 名称
     * @return 示例列表 [{title, code}]
     */
    @GetMapping("/sql-examples/{schema}/{label}")
    public Result<List<Map<String, String>>> sqlExamples(
            @PathVariable String schema,
            @PathVariable String label) {
        return Result.ok(service.generateSqlExamples(schema, label));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
