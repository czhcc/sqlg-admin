# Sqlg 图数据库管理平台 (Sqlg Graph Management Platform)

基于 Web 的 **Sqlg 图数据库统一管理平台**，用于通过浏览器集中管理多个 Sqlg 图库连接、查看 Sqlg Topology、维护 VertexLabel / EdgeLabel、浏览点/边数据、执行 Gremlin 查询、导入导出图数据，并提供面向 Sqlg 的图结构管理与运维辅助能力。

本平台面向使用 **Sqlg + PostgreSQL** 构建图数据库的开发与运维场景，目标是提供一个轻量、直观、可视化的 Sqlg 管理后台，降低直接使用 Gremlin Console、数据库客户端或手写 Topology API 的操作成本。

平台重点管理的是 **Sqlg 图库本身**，包括图连接、Schema、Topology、点类型、边类型、属性、Identifier、索引以及点边实例数据。它不是知识图谱本体平台，也不绑定特定业务模型，而是一个通用的 Sqlg 图数据库管理工具。

## 核心能力

* 管理多个 Sqlg 图数据库连接
* 查看当前连接下的 Sqlg Topology
* 管理 Schema、VertexLabel、EdgeLabel
* 管理点类型和边类型上的属性字段
* 支持字符串 Identifier / 复合 Identifier 配置
* 支持常用 Sqlg 属性类型，包括 JSON / JSONB 属性
* 浏览、查询、新增、编辑、删除点数据
* 浏览、查询、新增、编辑、删除边数据
* 执行 Gremlin 查询并查看查询结果
* 支持点边关系的图形化展示与关系展开
* 支持点数据、边数据、查询结果的导入导出
* 提供操作日志、危险操作确认、查询限制等基础管理能力

## 平台定位

Sqlg 是基于 Apache TinkerPop 的图数据库实现，可以将图模型映射到底层关系型数据库表结构中，例如 PostgreSQL 中的 `V_` 点表、`E_` 边表以及 `sqlg_schema` Topology 元数据。

本平台围绕 Sqlg 的这些特性进行管理封装，帮助用户完成：

* 从 Web 页面管理 Sqlg 图库连接
* 从 Topology 视角查看和维护图结构
* 从 Gremlin 视角查询和操作图数据
* 从数据库表结构视角理解 Sqlg 的底层映射
* 避免直接修改底层表导致 Topology 与物理表结构不一致

## 适用场景

* Sqlg 图数据库开发调试
* PostgreSQL 上的图数据管理
* 多个 Sqlg 图库连接统一维护
* Gremlin 查询验证与结果查看
* VertexLabel / EdgeLabel / 属性 / 索引管理
* 图数据导入、导出和运维辅助
* 替代部分 Gremlin Console 和数据库客户端的日常管理操作

> 后端基于 **Spring Boot 4.1 + MyBatis + sqlg-postgres**,前端基于 **Vite 8 + React 19 + Tailwind v4**。

---

## 功能特性

- 🔐 **JWT 鉴权登录** — 无状态会话,初始账号 `admin / 123456`
- 🔌 **连接管理** — 多图数据库连接配置(完整 CRUD + 测试连接 + 启停 + 默认连接),支持 PostgreSQL / H2 / HSQLDB / MariaDB / MySQL
- 🗺️ **拓扑浏览** — 以树形结构查看图数据库的 Schema / VertexLabel / EdgeLabel / 属性 / 索引,支持连接切换与缓存刷新
- 🏷️ **点类型管理** — VertexLabel 的 CRUD、清空点数据、关联边查看、底层表结构查看、Gremlin/SQL 示例生成
- 🔗 **边类型管理** — EdgeLabel 的列表/新增/删除,选择 out/in VertexLabel,配置属性/identifier,边方向预览与 Gremlin 示例
- 🔗 **图关系展开** — 基于 AntV G6 的图关系可视化,支持连接/Schema 选择、点查询上图、双击展开邻居、多布局切换(力导向/层级/辐射/环形/网格)、节点类型着色与新节点高亮
- 🖥️ **Gremlin 控制台** — CodeMirror 编辑器,支持方法自动补全、选中片段单独执行、Ctrl+Enter 快捷键、三级安全模式(只读/读写/管理员)、五种结果视图(表格/JSON/图形/路径/原始)、查询历史与收藏管理
- 📤 **导入导出** — 支持 CSV/JSON 格式的点/边数据导入导出,导入预览、字段映射、错误行跟踪、覆盖模式,以及 Topology 结构导出/导入(环境间迁移)
- 📋 **操作日志** — 记录所有关键操作(连接/拓扑/点边数据/Gremlin/导入导出),支持分页筛选、详情查看、危险操作标记、JDBC URL 脱敏
- 📊 **Flyway 数据库迁移** — 版本化的 schema 演进
- 🎨 **响应式左右布局** — 左侧菜单 + 右侧功能区
- 🌐 **主机 IP 访问** — 开发服务器支持局域网访问

## 技术栈

### 后端

| 层 | 组件 | 版本 |
|----|------|------|
| JDK | OpenJDK | 21 |
| 框架 | Spring Boot | 4.1.0 |
| Web | spring-boot-starter-web (Tomcat 11, Jakarta EE) | 4.1.0 |
| 安全 | spring-boot-starter-security (JWT) | 4.1.0 |
| ORM | MyBatis (XML mapper) | 3.5.19 |
| MyBatis starter | mybatis-spring-boot-starter | 4.0.1 |
| 数据库 | PostgreSQL | 18 |
| 数据库迁移 | Flyway | 12.4.0 |
| 图 ORM | sqlg-postgres (TinkerPop 3.7.4) | 3.1.6 |
| 连接池 | HikariCP | 7.0.2 |
| JWT | jjwt | 0.12.6 |
| 工具 | Lombok | BOM 管理 |

### 前端

| 层 | 组件 | 版本 |
|----|------|------|
| 构建 | Vite | 8.x |
| 框架 | React | 19 |
| 路由 | react-router-dom | 7 |
| 样式 | Tailwind CSS | v4 (`@tailwindcss/vite`,无 config.js) |
| HTTP | axios | 1.x |
| 图标 | lucide-react | 1.x |

## 目录结构

```
graph_app/
├── AGENTS.md                        # 完整项目文档(必读)
├── README.md                        # 本文件
├── backend/                         # Spring Boot 后端 (port 8090, ctx /api)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/trs/
│       │   ├── GraphMngApplication.java
│       │   ├── common/              # Result / PageResult / GlobalExceptionHandler
│       │   ├── config/              # Security / Jwt / Sqlg / BeanConfig
│       │   ├── security/            # JwtAuthFilter
│       │   ├── user/                # 用户 + 登录
│       │   └── modules/             # 业务模块(connection/topology/vertexType/edgeType 已实现,其余 stub)
│       └── resources/
│           ├── application.yml
│           ├── sqlg.properties
│           ├── mapper/*.xml
│           └── db/migration/        # Flyway: V1__init_schema.sql
└── frontend/                        # Vite + React SPA (port 5173)
    ├── package.json
    ├── vite.config.js
    ├── .env.example
    └── src/
        ├── App.jsx                  # 路由表
        ├── api/                     # client.js (axios + JWT) / auth / connection / topology / vertexType / edgeType
        ├── context/AuthContext.jsx
        ├── components/              # Layout / ProtectedRoute / Placeholder
        └── pages/                   # 10 个功能页 + Login
```

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+(本机若未安装,可下载解压使用)
- Node.js 18+ / npm
- PostgreSQL 14+(或使用已有实例)

### 1. 准备数据库

```sql
-- 在 PostgreSQL 中创建库和用户
CREATE DATABASE sqlgmngdb;
CREATE USER sqlg_mng WITH PASSWORD 'sqlg_mng';
GRANT ALL PRIVILEGES ON DATABASE sqlgmngdb TO sqlg_mng;
```

修改 `backend/src/main/resources/application.yml` 中的 `spring.datasource.*` 指向你的 PostgreSQL 实例。Flyway 会在首次启动时自动建表并插入初始 admin 用户。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8090/api
```

启动日志应包含:
```
Flyway: Migrating schema "public" to version "1 - init schema"
Flyway: Successfully applied 1 migration to schema "public", now at version v1
Tomcat started on port 8090
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

### 4. 访问

浏览器打开 `http://localhost:5173`(或主机 IP),使用初始账号登录:

```
用户名: admin
密码:   123456
```

> **主机 IP 访问**: `vite.config.js` 已配置 `host: '0.0.0.0'` + `allowedHosts: true`,可用 `http://<主机IP>:5173` 直接访问。后端代理默认指向 `http://localhost:8090`,如需修改复制 `.env.example` 为 `.env.development`。

## 配置说明

### 后端 (`application.yml` 关键项)

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8090 | 后端端口 |
| `server.servlet.context-path` | /api | API 前缀 |
| `spring.datasource.*` | 192.168.31.112/sqlgmngdb | 主数据源 (MyBatis 用) |
| `spring.flyway.locations` | classpath:db/migration | Flyway 迁移脚本位置 |
| `mybatis.mapper-locations` | classpath:mapper/*.xml | MyBatis XML 映射 |
| `mybatis.configuration.map-underscore-to-camel-case` | true | 自动驼峰映射 |
| `sqlg.enabled` | true | 是否初始化 SqlgGraph(关掉不影响 Web) |
| `app.jwt.secret / expiration-ms` | — | JWT 签名密钥 / 有效期(默认 24h) |

### 前端环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `VITE_API_TARGET` | http://localhost:8090 | Vite 开发代理 / API 后端地址 |
| `VITE_API_BASE` | /api | axios baseURL(生产构建可覆盖) |

## 数据库 schema

由 Flyway 管理,首个迁移 `V1__init_schema.sql` 创建三张表:

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户表(初始 admin / 123456) |
| `sys_operation_log` | 操作日志表 |
| `sys_graph_connection` | 图数据库连接配置表 |

每张表和每个字段都有 `COMMENT`。新增迁移文件按 `V<n>__<desc>.sql` 命名,Flyway 启动时自动应用,**不要修改已发布的迁移文件**。

## 实现进度

| 模块 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 连接管理 | ✅ | ✅ | 完整实现(CRUD + 测试 + 启停 + 默认) |
| Topology 浏览 | ✅ | ✅ | 完整实现(树形展开 + 连接记忆 + 刷新) |
| 点类型管理 | ✅ | ✅ | 完整实现(CRUD + 清空点 + 关联边 + 表结构 + 示例) |
| 边类型管理 | ✅ | ✅ | 完整实现(列表 + 新增 + 删除 + 清空 + 表结构 + 示例 + 方向预览) |
| 属性管理 | ✅ | ✅ | 完整实现(属性树 + 连接记忆 + 刷新 + 属性 CRUD + 索引管理 + UI 元数据) |
| 点数据管理 | ✅ | ✅ | 完整实现(分页查询 + 属性过滤 + 点详情 + 新增/编辑/删除 + 批量删除 + 清空 + identifier 支持 + 关联边) |
| 边数据管理 | ✅ | ✅ | 完整实现(分页查询 + 出/入点过滤 + 边详情 + 新增/编辑/删除 + 批量删除 + 清空 + 导出 + 顶点选择器) |
| 图关系展开 | ✅ | ✅ | 完整实现(连接选择 + Schema 选择 + 点查询弹窗 + G6 图谱 + 双击展开 + 多布局 + 类型着色 + 高亮新节点) |
| Gremlin 控制台 | ✅ | ✅ | 完整实现(CodeMirror 编辑器 + 选中执行 + Ctrl+Enter 快捷键 + 方法自动补全 + 安全模式 + 五种结果视图 + 查询历史 + 收藏管理 + 示例) |
| 导入导出 | ✅ | ✅ | 完整实现(点/边数据 CSV/JSON 导入导出 + 导入预览 + 字段映射 + 错误行跟踪 + Topology 导出/导入 + 覆盖模式) |
| 操作日志 | ✅ | ✅ | 完整实现(操作记录 + 分页筛选 + 详情 + 危险操作标记 + Gremlin/导入导出/点边数据/拓扑结构操作全覆盖 + JDBC脱敏) |

## API 概览

所有 API 都以 `/api` 为前缀,除 `/auth/login` 外都需要 `Authorization: Bearer <token>` 头。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录,返回 JWT |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/info` | 获取当前用户信息 |
| GET | `/connection` | 列出所有连接(支持 keyword 搜索) |
| POST | `/connection` | 新增连接 |
| PUT | `/connection/{id}` | 编辑连接(密码留空则不修改) |
| DELETE | `/connection/{id}` | 删除连接 |
| POST | `/connection/test` | 测试连接(填表时,不入库) |
| POST | `/connection/{id}/test` | 测试已存的连接 |
| PUT | `/connection/{id}/status` | 启用/停用 `{status: 0\|1}` |
| PUT | `/connection/{id}/default` | 设为默认连接 |
| GET | `/topology/connections` | 列出可用于拓扑浏览的连接 + 用户上次选择 |
| GET | `/topology/{connectionId}` | 获取指定连接的完整拓扑(Schema / VertexLabel / EdgeLabel / 属性 / 索引) |
| POST | `/topology/{connectionId}/refresh` | 清除该连接的拓扑缓存,下次查询重新加载 |
| PUT | `/topology/active-connection` | 记住用户在拓扑页选择的连接 `{connectionId: Long\|null}` |
| GET | `/vertex-type/connections` | 列出可用的连接 + 用户上次选择 |
| PUT | `/vertex-type/active-connection` | 记住用户选择的连接 `{connectionId: Long\|null}` |
| GET | `/vertex-type/{connectionId}` | 列出该连接下所有 VertexLabel |
| GET | `/vertex-type/{connectionId}/{schema}/{label}` | 获取单个 VertexLabel 详情(属性 / 索引 / 标识符) |
| POST | `/vertex-type/{connectionId}` | 新增 VertexLabel |
| PUT | `/vertex-type/{connectionId}` | 编辑 VertexLabel(新增缺失属性) |
| DELETE | `/vertex-type/{connectionId}` | 删除 VertexLabel 及底层 V_XXX 表 `{schema, label}` |
| POST | `/vertex-type/{connectionId}/clear-vertices` | 仅清空点数据,保留 VertexLabel 定义 |
| GET | `/vertex-type/{connectionId}/{schema}/{label}/edges` | 查看关联边类型(入边 + 出边) |
| GET | `/vertex-type/{connectionId}/{schema}/{label}/table-schema` | 查看底层物理表结构 |
| GET | `/vertex-type/gremlin-examples/{schema}/{label}` | 生成 Gremlin 示例查询 |
| GET | `/vertex-type/sql-examples/{schema}/{label}` | 生成 SQL 示例查询 |
| GET | `/edge-type/connections` | 列出可用的连接 + 用户上次选择 |
| PUT | `/edge-type/active-connection` | 记住用户选择的连接 `{connectionId: Long\|null}` |
| GET | `/edge-type/{connectionId}` | 列出该连接下所有 EdgeLabel |
| GET | `/edge-type/{connectionId}/{schema}/{label}` | 获取单个 EdgeLabel 详情(属性 / 索引 / 出入点类型) |
| POST | `/edge-type/{connectionId}` | 新增 EdgeLabel(需指定 out/in 点类型) |
| DELETE | `/edge-type/{connectionId}` | 删除 EdgeLabel 及底层 E_XXX 表 `{schema, label}` |
| POST | `/edge-type/{connectionId}/clear-edges` | 仅清空边数据,保留 EdgeLabel 定义 |
| GET | `/edge-type/{connectionId}/{schema}/{label}/table-schema` | 查看底层物理表结构 |
| GET | `/edge-type/{connectionId}/vertex-labels` | 列出所有 VertexLabel(供新增边表单选择出入点) |
| GET | `/edge-type/gremlin-examples/{schema}/{label}` | 生成 Gremlin 示例查询 |
| GET | `/edge-type/sql-examples/{schema}/{label}` | 生成 SQL 示例查询 |

统一响应格式:
```json
{ "code": 0, "message": "success", "data": {...} }
```
`code = 0` 表示成功,非 0 表示业务错误(`code = 400` 参数错误,`code = 401` 未登录,`code = 500` 服务异常)。

## 开发指南

更详细的项目结构、技术栈说明、开发约定和踩坑记录请阅读 **[AGENTS.md](./AGENTS.md)**。

### 启动方式(tmux 后台)

```bash
# 后端
tmux new-session -d -s backend -c backend
tmux send-keys -t backend "mvn spring-boot:run" Enter

# 前端
tmux new-session -d -s frontend -c frontend
tmux send-keys -t frontend "npm run dev" Enter

# 查看日志
tmux attach -t backend    # Ctrl+B D 退出

# 停止
tmux kill-session -t backend
```

### 项目约定

- **包前缀**: `com.trs`,业务模块放 `com.trs.modules.<name>/`
- **响应统一**: Controller 返回 `Result<T>`,错误抛 `IllegalArgumentException` 转 400
- **字段映射**: DB `snake_case` ↔ Java `camelCase`,**禁止**在 MyBatis XML 手写别名
- **API 调用**: 前端必经 `src/api/client.js`,**禁止**裸 `fetch` / 裸 `axios`
- **样式**: 仅用 Tailwind utility class,不写自定义 CSS
- **图标**: `lucide-react`,按需 import
- **类型安全**: 禁止 `@ts-ignore` / `as any` / `@SuppressWarnings` 掩盖错误
- **注释**: 代码尽量自解释;DDL 必须有 COMMENT;复杂逻辑(算法/正则/安全/魔数)才加注释

## License

MIT
