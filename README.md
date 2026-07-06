# 图数据库管理平台 (Graph Database Management Platform)

基于 Web 的图数据库统一管理平台,通过浏览器即可管理多个图数据库连接、查看拓扑、浏览点/边数据、执行 Gremlin 查询、导入导出数据。

> 后端基于 **Spring Boot 4.1 + MyBatis + sqlg-postgres**,前端基于 **Vite 8 + React 19 + Tailwind v4**。

---

## 功能特性

- 🔐 **JWT 鉴权登录** — 无状态会话,初始账号 `admin / 123456`
- 🔌 **连接管理** — 多图数据库连接配置(完整 CRUD + 测试连接 + 启停 + 默认连接),支持 PostgreSQL / H2 / HSQLDB / MariaDB / MySQL
- 🗺️ **拓扑浏览 / 点类型 / 边类型 / 点数据 / 边数据 / 图关系展开 / Gremlin 控制台 / 导入导出 / 操作日志** — 10 个功能模块(连接管理已实现,其余为占位骨架)
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
│       │   └── modules/             # 业务模块(connection 已实现,其余 stub)
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
        ├── api/                     # client.js (axios + JWT) / auth / connection
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
| Topology 浏览 | stub | 占位 | 待实现 |
| 点类型管理 | stub | 占位 | 待实现 |
| 边类型管理 | stub | 占位 | 待实现 |
| 点数据浏览 | stub | 占位 | 待实现 |
| 边数据浏览 | stub | 占位 | 待实现 |
| 图关系展开 | stub | 占位 | 待实现 |
| Gremlin 控制台 | stub | 占位 | 待实现 |
| 导入导出 | stub | 占位 | 待实现 |
| 操作日志 | stub | 占位 | 待实现 |

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
