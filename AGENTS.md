# AGENTS.md — 图数据库管理平台

> 本文件描述项目结构、技术栈与开发约定,供任何接手本项目的工程师或 AI agent 快速建立完整上下文。

## 1. 项目概览

- **项目名**: 图数据库管理平台 (Graph Database Management Platform)
- **目标**: 通过 Web 界面统一管理多个图数据库连接,支持 Topology 浏览、点/边类型管理、点/边数据浏览、图关系展开、Gremlin 控制台、导入导出、操作日志。
- **结构**: 两个独立工程
  - `backend/` — Spring Boot 4.1 REST API,端口 `8090`,context-path `/api`
  - `frontend/` — Vite + React 19 SPA,端口 `5173`,开发代理 `/api → http://localhost:8090`

## 2. 技术栈

### 后端 (`backend/`)

| 层 | 组件 | 版本 | 说明 |
|----|------|------|------|
| JDK | OpenJDK | 21 | `pom.xml` 中 `maven.compiler.release=21` |
| 构建 | Maven | 3.9+ | `pom.xml` |
| 框架 | Spring Boot | **4.1.0** | parent: `spring-boot-starter-parent:4.1.0` |
| Web | spring-boot-starter-web | 4.1.0 | 内嵌 Tomcat 11,Jakarta EE namespace |
| 安全 | spring-boot-starter-security | 4.1.0 | JWT 无状态鉴权 |
| ORM | MyBatis | 3.5.19 | XML mapper,`map-underscore-to-camel-case=true` |
| MyBatis starter | mybatis-spring-boot-starter | **4.0.1** | Spring Boot 4.x 必须用 4.x |
| 数据库 | PostgreSQL | 18 (服务端) | 默认连接 `192.168.31.112:5432/sqlgmngdb` |
| JDBC 驱动 | org.postgresql:postgresql | 42.7.11 | BOM 管理 |
| 连接测试驱动 | h2 / hsqldb / mariadb / mysql | BOM 管理 | 用于连接管理「测试连接」功能 |
| 数据库迁移 | **Flyway** | 12.4.0 | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| 图 ORM | sqlg-postgres | **3.1.6** | TinkerPop 3.7.4 实现,文档 sqlg.org/docs/3.1.6 |
| 连接池 | HikariCP / sqlg-hikari | 7.0.2 / 3.1.6 | Spring 主数据源 + sqlg 独立数据源 |
| JWT | jjwt | 0.12.6 | `jjwt-api` + `jjwt-impl` + `jjwt-jackson` |
| 工具 | Lombok | BOM 管理 | 需在 `maven-compiler-plugin` 配 `annotationProcessorPaths` |

> **关键事实**: Spring Boot 4.1 把 Flyway/JDBC/JPA 等 autoconfig 拆到了独立 starter(如 `spring-boot-starter-flyway` 而非裸 `flyway-core`)。只引 `flyway-core` 不会触发 autoconfig。

### 前端 (`frontend/`)

| 层 | 组件 | 版本 | 说明 |
|----|------|------|------|
| 构建 | Vite | 8.x | `vite.config.js` |
| 框架 | React | 19 | JSX,StrictMode |
| 路由 | react-router-dom | 7 | `BrowserRouter` + 嵌套路由 |
| 样式 | Tailwind CSS | **v4** | `@tailwindcss/vite` 插件,**无 `tailwind.config.js`**,CSS 用 `@import "tailwindcss"` |
| HTTP | axios | 1.x | 统一 `client.js`,带 JWT 拦截器 |
| 图标 | lucide-react | 1.x | 树摇友好 |

## 3. 目录结构

```
graph_app/
├── AGENTS.md                       ← 本文件
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/trs/
│       │   ├── GraphMngApplication.java   ← @SpringBootApplication 入口
│       │   ├── common/
│       │   │   ├── Result.java            ← 统一响应 {code,message,data}
│       │   │   ├── PageResult.java        ← 分页响应 {total,rows}
│       │   │   └── GlobalExceptionHandler.java  ← @RestControllerAdvice,IllegalArgumentException→400
│       │   ├── config/
│       │   │   ├── BeanConfig.java        ← PasswordEncoder 等
│       │   │   ├── SecurityConfig.java    ← SecurityFilterChain + CORS + JWT 入口点
│       │   │   ├── JwtUtil.java           ← JJWT 0.12 API
│       │   │   └── SqlgConfig.java        ← @ConditionalOnProperty("sqlg.enabled") 启动 SqlgGraph
│       │   ├── security/
│       │   │   └── JwtAuthFilter.java     ← OncePerRequestFilter,解析 Bearer token
│       │   ├── user/
│       │   │   ├── entity/User.java
│       │   │   ├── mapper/UserMapper.java
│       │   │   ├── service/UserService.java
│       │   │   └── controller/AuthController.java  ← /auth/login /auth/logout /auth/info
│       │   └── modules/
│       │       ├── connection/            ← ✅ 已实现完整 CRUD + 测试 + 启停 + 默认
│       │       │   ├── ConnectionController.java
│       │       │   ├── entity/GraphConnection.java
│       │       │   ├── mapper/GraphConnectionMapper.java
│       │       │   └── service/GraphConnectionService.java
│       │       ├── topology/              ← ✅ 已实现(树形浏览 + 连接记忆)
│       │       ├── vertexType/            ← ✅ 已实现(CRUD + 清空 + 关联边 + 表结构 + 示例)
│       │       ├── edgeType/              ← ✅ 已实现(列表 + 新增 + 删除 + 清空 + 方向预览)
│       │       ├── vertexData/            ← 占位 stub
│       │       ├── edgeData/              ← 占位 stub
│       │       ├── graphExplore/          ← 占位 stub
│       │       ├── gremlin/               ← 占位 stub
│       │       ├── io/                    ← 占位 stub (import/export)
│       │       └── log/                   ← 占位 stub (OperationLogController)
│       └── resources/
│           ├── application.yml
│           ├── sqlg.properties            ← sqlg 数据源 (jdbc.url/username/password)
│           ├── mapper/
│           │   ├── UserMapper.xml
│           │   └── GraphConnectionMapper.xml
│           └── db/migration/              ← Flyway 迁移脚本
│               └── V1__init_schema.sql    ← sys_user / sys_operation_log / sys_graph_connection
└── frontend/
    ├── package.json
    ├── vite.config.js                    ← host=0.0.0.0 + allowedHosts + /api 代理
    ├── index.html
    ├── .env.example                      ← VITE_API_TARGET / VITE_API_BASE
    └── src/
        ├── main.jsx                      ← createRoot 入口
        ├── App.jsx                       ← 路由表 (10 个菜单 + login)
        ├── index.css                     ← @import "tailwindcss"
        ├── api/
        │   ├── client.js                 ← axios 实例 + JWT 拦截 + 401 自动跳登录
        │   ├── auth.js                   ← login/logout/getUserInfo
        │   └── connection.js             ← 连接管理 9 个 API
        ├── context/
        │   └── AuthContext.jsx           ← useAuth() / AuthProvider
        ├── components/
        │   ├── ProtectedRoute.jsx        ← 未登录重定向 /login
        │   ├── Layout.jsx                ← 左侧菜单(10 项 + lucide 图标)+ 右侧 children
        │   └── Placeholder.jsx           ← 未实现功能的占位页
        └── pages/
            ├── Login.jsx                 ← 登录页 (admin/123456)
            ├── Connection.jsx            ← ✅ 完整列表+模态框 CRUD+行内操作
            ├── Topology.jsx              ← ✅ 完整树形浏览+连接记忆
            ├── VertexType.jsx            ← ✅ 完整列表+模态框 CRUD+行内操作
            ├── EdgeType.jsx              ← ✅ 完整列表+新增(出/入点选择)+方向预览+删除
            ├── VertexData.jsx            ← 占位
            ├── EdgeData.jsx              ← 占位
            ├── GraphExplore.jsx          ← 占位
            ├── GremlinConsole.jsx        ← 占位
            ├── ImportExport.jsx          ← 占位
            └── OperationLog.jsx          ← 占位
```

## 4. 数据库 (Flyway 管理)

- **迁移位置**: `backend/src/main/resources/db/migration/`
- **命名规范**: `V<版本号>__<描述>.sql`(双下划线),如 `V1__init_schema.sql`
- **已有迁移**:
  - `V1__init_schema.sql` — 创建 `sys_user`、`sys_operation_log`、`sys_graph_connection` + 初始 admin 用户
- **添加新迁移**: 新建 `V2__xxx.sql`,Flyway 启动时自动应用,**不要修改已发布过的迁移文件**(checksum 会校验失败)
- **每个表/字段必须有 `COMMENT ON TABLE` / `COMMENT ON COLUMN`**(项目硬约定)
- **历史表**: `flyway_schema_history`(Flyway 自动维护,勿手动改)
- 重置开发库: `DELETE FROM flyway_schema_history; DROP TABLE sys_*; ` 后重启

## 5. 配置约定

### 后端关键配置 (`application.yml`)

```yaml
server.port: 8090
server.servlet.context-path: /api
spring.datasource:        # 主数据源 (MyBatis 用)
spring.flyway.locations: classpath:db/migration
mybatis.mapper-locations: classpath:mapper/*.xml
mybatis.configuration.map-underscore-to-camel-case: true
sqlg.enabled: true        # false 则不初始化 SqlgGraph (Web 仍可用)
app.jwt.secret / expiration-ms
```

### 前端关键配置

- 开发: `npm run dev` → `http://localhost:5173`,Vite 代理 `/api` 到 `VITE_API_TARGET`(默认 `http://localhost:8090`)
- 生产构建: `npm run build` → `dist/`,axios `baseURL` 可通过 `VITE_API_BASE` 覆盖
- 主机 IP 访问: `vite.config.js` 已设 `host: '0.0.0.0'` + `allowedHosts: true`

## 6. 开发约定

### Java 后端

- **包前缀**: `com.trs`
- **模块化**: 业务功能放 `com.trs.modules.<moduleName>/`,每个模块自带 `entity/mapper/service/controller`
- **Mapper 扫描**: `@MapperScan("com.trs.**.mapper")`(在 `GraphMngApplication` 上),XML 在 `resources/mapper/`
- **响应统一**: 所有 Controller 返回 `Result<T>` 或 `Result<?>`,错误抛 `IllegalArgumentException` 由 `GlobalExceptionHandler` 转 400
- **字段映射**: DB `snake_case` ↔ Java `camelCase`,依赖 `map-underscore-to-camel-case`,**禁止**在 XML 里手写别名
- **类型安全**: 禁止 `@ts-ignore` / `as any` / `@SuppressWarnings` 掩盖错误
- **Javadoc 注释**: **所有类和 `public` 方法必须加 Javadoc 注释**;类注释中必须包含 `@author` 和 `@date`,其中 `@author` 使用当前系统用户名(即 `whoami` 输出,如 `czh`),`@date` 使用 `yyyy/MMdd` 格式的创建日期。示例:
  ```java
  /**
   * 图数据库连接管理服务,提供连接的 CRUD、启停、默认设置及连通性测试。
   *
   * @author czh
   * @date 2026/07/07
   */
  ```
- **注释**: 代码尽量自解释;DDL 必须有 COMMENT;方法体内复杂逻辑(算法/正则/安全/魔数)补行内注释
- **包名冲突坑**: sqlg 用 `org.apache.commons.configuration2.Configuration`,与 Spring 的 `@Configuration` 重名 → 在 `SqlgConfig` 上用全限定 `@org.springframework.context.annotation.Configuration`

### 前端

- **API 调用**: 必须经 `src/api/client.js`(自动注入 JWT、统一 401 处理),**禁止**裸 `fetch` / 裸 `axios`
- **路由**: 10 个功能页都在 `ProtectedRoute` 包裹下,新增页同步加到 `App.jsx` 路由表 + `Layout.jsx` 菜单
- **样式**: 仅用 Tailwind v4 utility class,**不要**新增 `tailwind.config.js`、不要写自定义 CSS(除非全局重置)
- **图标**: 优先 `lucide-react`,按需 import
- **表单弹窗**: 用 `fixed inset-0` 模态层,参考 `Connection.jsx` 实现

## 7. 启动方式

两个服务用独立 tmux 会话运行(脱离父 shell):

```bash
# 后端
tmux new-session -d -s backend -c /data/graph_app/backend
tmux send-keys -t backend "mvn spring-boot:run" Enter

# 前端
tmux new-session -d -s frontend -c /data/graph_app/frontend
tmux send-keys -t frontend "npm run dev" Enter

# 查看 / 停止
tmux attach -t backend           # Ctrl+B D 退出
tmux kill-session -t backend
```

- Maven 路径: `/tmp/opencode/maven/bin/mvn`(本机未全局安装)
- 访问: `http://<主机IP>:5173`,初始账号 `admin / 123456`

## 8. 实现进度

| 菜单 | 后端 Controller | 前端 Page | 状态 |
|------|----------------|-----------|------|
| 连接管理 | `ConnectionController` | `Connection.jsx` | ✅ 完整实现 |
| Topology 浏览 | `TopologyController` | `Topology.jsx` | ✅ 完整实现 |
| 点类型管理 | `VertexTypeController` | `VertexType.jsx` | ✅ 完整实现 |
| 边类型管理 | `EdgeTypeController` | `EdgeType.jsx` | ✅ 完整实现 |
| 点数据浏览 | stub | 占位 | ⬜ 待实现 |
| 边数据浏览 | stub | 占位 | ⬜ 待实现 |
| 图关系展开 | stub | 占位 | ⬜ 待实现 |
| Gremlin 控制台 | stub | 占位 | ⬜ 待实现 |
| 导入导出 | stub | 占位 | ⬜ 待实现 |
| 操作日志 | stub | 占位 | ⬜ 待实现 |

## 9. 踩过的坑(供后人参考)

1. **Spring Boot 4.1 Flyway autoconfig** — 不能只引 `flyway-core`,必须用 `spring-boot-starter-flyway`(autoconfig 已拆到 `spring-boot-flyway` 子模块)
2. **mybatis-spring-boot-starter** — Spring Boot 4.x 必须用 4.x 版本,3.x 不兼容
3. **sqlg 版本号** — `sqlg-postgres:3.16` 实际发布坐标是 `3.1.6`(用户口语 "3.16" = "3.1.6")
4. **SqlgGraph.open** — 接受 `org.apache.commons.configuration2.Configuration`,不接受 `java.util.Properties`
5. **maven-compiler-plugin 3.15** — Lombok 需在 `<annotationProcessorPaths>` 显式声明,父 pom 不再自动加
6. **Vite 主机 IP 访问** — `host: true` 不够,必须 `allowedHosts: true` 才能放过 DNS-rebinding 防护
7. **BCrypt 哈希** — `init.sql` 里的初始 admin 密码必须用真实 BCrypt 编码 "123456" 得到的 hash,不能照搬网上的示例 hash
8. **循环依赖** — `SecurityConfig` 里定义 `PasswordEncoder` bean 会与 `JwtAuthFilter → UserService` 形成环,需把 `PasswordEncoder` 拆到独立 `BeanConfig`
