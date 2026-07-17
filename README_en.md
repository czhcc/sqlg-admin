# Sqlg Graph Database Management Platform

A web-based **unified management platform for Sqlg graph databases**, enabling centralized management of multiple Sqlg graph connections through a browser — view Sqlg Topology, maintain VertexLabel / EdgeLabel, browse vertex/edge data, execute Gremlin queries, import/export graph data, and provide Sqlg-oriented graph structure management and operations assistance.

This platform targets development and operations scenarios that build graph databases with **Sqlg + PostgreSQL**. The goal is to provide a lightweight, intuitive, visualized Sqlg admin backend that reduces the operational cost of directly using the Gremlin Console, database clients, or hand-written Topology APIs.

The platform focuses on managing the **Sqlg graph database itself**, including graph connections, Schema, Topology, vertex types, edge types, properties, Identifiers, indexes, and vertex/edge instance data. It is not a knowledge-graph platform nor bound to a specific business model — it is a general-purpose Sqlg graph database management tool.

## Core Capabilities

* Manage multiple Sqlg graph database connections
* View the Sqlg Topology under the current connection
* Manage Schema, VertexLabel, EdgeLabel
* Manage property fields on vertex types and edge types
* Support string Identifier / composite Identifier configuration
* Support common Sqlg property types, including JSON / JSONB properties
* Browse, query, create, edit, and delete vertex data
* Browse, query, create, edit, and delete edge data
* Execute Gremlin queries and view query results
* Support graphical visualization and relational expansion of vertex-edge relationships
* Support import/export of vertex data, edge data, and query results
* Provide basic management capabilities such as operation logs, dangerous-operation confirmation, and query limits

## Platform Positioning

Sqlg is a graph database implementation based on Apache TinkerPop that maps graph models onto underlying relational table structures — for example, `V_` vertex tables and `E_` edge tables in PostgreSQL, as well as the `sqlg_schema` Topology metadata.

This platform wraps these Sqlg characteristics to help users:

* Manage Sqlg graph connections from a web UI
* View and maintain graph structure from a Topology perspective
* Query and manipulate graph data from a Gremlin perspective
* Understand Sqlg's underlying mapping from a database-table-structure perspective
* Avoid topology/physical-table inconsistency caused by directly modifying underlying tables

## Applicable Scenarios

* Sqlg graph database development and debugging
* Graph data management on PostgreSQL
* Unified maintenance of multiple Sqlg graph connections
* Gremlin query validation and result viewing
* VertexLabel / EdgeLabel / property / index management
* Graph data import, export, and operational assistance
* Replacing some daily management operations of the Gremlin Console and database clients

> The backend is built on **Spring Boot 4.1 + MyBatis + sqlg-postgres**; the frontend on **Vite 8 + React 19 + Tailwind v4**.

---

## Features

- 🔐 **JWT Authentication Login** — stateless sessions, initial account `admin / 123456`
- 🔌 **Connection Management** — multi-graph-database connection config (full CRUD + test connection + enable/disable + default connection), supporting PostgreSQL / H2 / HSQLDB / MariaDB / MySQL
- 🗺️ **Topology Browsing** — tree-structured view of Schema / VertexLabel / EdgeLabel / properties / indexes, with connection switching and cache refresh
- 🏷️ **Vertex Type Management** — VertexLabel CRUD, clear vertex data, view related edges, inspect underlying table structure, Gremlin/SQL example generation
- 🔗 **Edge Type Management** — EdgeLabel list/create/delete, select out/in VertexLabel, configure properties/identifier, edge direction preview and Gremlin examples
- 🔗 **Graph Relationship Expansion** — AntV G6-based graph visualization, supporting connection/schema selection, vertex query upload, double-click neighbor expansion, multi-layout switching (force-directed/hierarchical/radial/circular/grid), node-type coloring and new-node highlighting
- 🖥️ **Gremlin Console** — CodeMirror editor with method autocompletion, selected-snippet execution, Ctrl+Enter shortcut, three-tier security mode (read-only/read-write/admin), five result views (table/JSON/graph/path/raw), query history and favorites management
- 📤 **Import/Export** — CSV/JSON import/export of vertex/edge data, import preview, field mapping, error-row tracking, overwrite mode, plus Topology export/import (cross-environment migration)
- 📋 **Operation Log** — records all key operations (connection/topology/vertex-edge data/Gremlin/import-export), with paginated filtering, detail view, dangerous-operation flagging, and JDBC URL masking
- 📊 **Flyway Database Migration** — versioned schema evolution
- 🎨 **Responsive Left-Right Layout** — left-side menu + right-side functional area
- 🌐 **Host-IP Access** — dev server supports LAN access

## Tech Stack

### Backend

| Layer | Component | Version |
|-------|-----------|---------|
| JDK | OpenJDK | 21 |
| Framework | Spring Boot | 4.1.0 |
| Web | spring-boot-starter-web (Tomcat 11, Jakarta EE) | 4.1.0 |
| Security | spring-boot-starter-security (JWT) | 4.1.0 |
| ORM | MyBatis (XML mapper) | 3.5.19 |
| MyBatis starter | mybatis-spring-boot-starter | 4.0.1 |
| Database | PostgreSQL | 18 |
| DB Migration | Flyway | 12.4.0 |
| Graph ORM | sqlg-postgres (TinkerPop 3.7.4) | 3.1.6 |
| Connection Pool | HikariCP | 7.0.2 |
| JWT | jjwt | 0.12.6 |
| Utility | Lombok | BOM-managed |

### Frontend

| Layer | Component | Version |
|-------|-----------|---------|
| Build | Vite | 8.x |
| Framework | React | 19 |
| Routing | react-router-dom | 7 |
| Styling | Tailwind CSS | v4 (`@tailwindcss/vite`, no config.js) |
| HTTP | axios | 1.x |
| Icons | lucide-react | 1.x |

## Directory Structure

```
graph_app/
├── AGENTS.md                        # Full project documentation (must-read)
├── README.md                        # This file
├── .dockerignore                    # Docker build context exclusions
├── docker/                          # Docker deployment (see "Docker Container Deployment" section)
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   ├── nginx.conf
│   ├── docker-compose.yml
│   └── postgres/init.sql
├── backend/                         # Spring Boot backend (port 8090, ctx /api)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/trs/
│       │   ├── GraphMngApplication.java
│       │   ├── common/              # Result / PageResult / GlobalExceptionHandler
│       │   ├── config/              # Security / Jwt / Sqlg / BeanConfig
│       │   ├── security/            # JwtAuthFilter
│       │   ├── user/                # Users + login
│       │   └── modules/             # Business modules (connection/topology/vertexType/edgeType implemented; others stubs)
│       └── resources/
│           ├── application.yml      # Common config + default profile=local
│           ├── application-local.yml# Dev datasource (192.168.31.112)
│           ├── application-prod.yml # Container datasource (${DB_HOST} env vars)
│           ├── sqlg.properties
│           ├── mapper/*.xml
│           └── db/migration/        # Flyway: V1__init_schema.sql
└── frontend/                        # Vite + React SPA (port 5173)
    ├── package.json
    ├── vite.config.js
    ├── .env.example
    └── src/
        ├── App.jsx                  # Route table
        ├── api/                     # client.js (axios + JWT) / auth / connection / topology / vertexType / edgeType
        ├── context/AuthContext.jsx
        ├── components/              # Layout / ProtectedRoute / Placeholder
        └── pages/                   # 10 feature pages + Login
```

## Quick Start

### Prerequisites

- JDK 21
- Maven 3.9+ (download and extract if not installed locally)
- Node.js 18+ / npm
- PostgreSQL 14+ (or use an existing instance)

### 1. Prepare the Database

```sql
-- Create database and user in PostgreSQL
CREATE DATABASE sqlgmngdb;
CREATE USER sqlg_mng WITH PASSWORD 'sqlg_mng';
GRANT ALL PRIVILEGES ON DATABASE sqlgmngdb TO sqlg_mng;
```

Modify `spring.datasource.*` in `backend/src/main/resources/application-local.yml` to point to your PostgreSQL instance. Flyway will auto-create tables and insert the initial admin user on first startup.

### 2. Start the Backend

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8090/api
```

The startup log should contain:
```
Flyway: Migrating schema "public" to version "1 - init schema"
Flyway: Successfully applied 1 migration to schema "public", now at version v1
Tomcat started on port 8090
```

### 3. Start the Frontend

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

### 4. Access

Open `http://localhost:5173` (or the host IP) in your browser and log in with the initial account:

```
Username: admin
Password: 123456
```

> **Host-IP Access**: `vite.config.js` is configured with `host: '0.0.0.0'` + `allowedHosts: true`, so you can access it directly via `http://<HOST_IP>:5173`. The backend proxy defaults to `http://localhost:8090`; to change it, copy `.env.example` to `.env.development`.

---

## Docker Container Deployment

If you don't want to install JDK / Maven / Node locally, you can bring up the entire stack (PostgreSQL + backend + frontend) with Docker in one command.

### Prerequisites

- Docker 24+
- Docker Compose v2 (the `docker compose` subcommand)

### Directory Structure

```
docker/
├── Dockerfile.backend          # Backend image: maven build → JRE runtime
├── Dockerfile.frontend         # Frontend image: npm build → nginx serving
├── nginx.conf                  # nginx config: static assets + /api reverse proxy to backend
├── docker-compose.yml          # Orchestration: postgres + backend + frontend
└── postgres/
    └── init.sql                # PG init script (creates pgvector extension)
```

### One-Command Startup

Run in the `docker/` directory:

```bash
cd docker
docker compose up -d --build
```

The first build pulls base images and compiles the frontend and backend, taking longer (about 5-10 minutes). Once started:

| Service | Container | Address | Description |
|---------|-----------|---------|-------------|
| Frontend | graph-mng-frontend | http://localhost | nginx serves SPA, `/api` reverse-proxied to backend |
| Backend | graph-mng-backend | http://localhost:8090/api | Spring Boot, prod profile |
| Database | graph-mng-postgres | localhost:5432 | pgvector/pgvector:pg18, database name `sqlgmngdb` |

Open `http://localhost` in your browser and log in with `admin / 123456`.

### Profile Explanation

The backend distinguishes runtime environments via Spring Profile:

| Profile | Config File | Datasource | Activation |
|---------|-------------|------------|------------|
| `local` (default) | `application-local.yml` | `192.168.31.112:5432` (LAN PG) | Auto-used by local `mvn spring-boot:run` |
| `prod` | `application-prod.yml` | `${DB_HOST}` and other env vars | `SPRING_PROFILES_ACTIVE=prod` inside the container |

The database connection in `application-prod.yml` is read via `${DB_HOST}` / `${DB_PORT}` / `${DB_NAME}` / `${DB_USER}` / `${DB_PASSWORD}` placeholders, whose values are injected by the `environment` block of `docker-compose.yml`.

### Customization

To change the database credentials or name, edit the `environment` of both the `postgres` and `backend` services in `docker-compose.yml` — **the two must stay consistent**:

```yaml
services:
  postgres:
    environment:
      POSTGRES_USER: sqlg_mng        # ← change here too
      POSTGRES_PASSWORD: sqlg_mng    # ←
      POSTGRES_DB: sqlgmngdb         # ←
  backend:
    environment:
      DB_USER: sqlg_mng              # ← must match POSTGRES_USER above
      DB_PASSWORD: sqlg_mng          # ← must match POSTGRES_PASSWORD above
      DB_NAME: sqlgmngdb             # ←
```

> After changing the password you must remove the old volume and rebuild: `docker compose down -v && docker compose up -d --build`

### Common Commands

```bash
cd docker

# View logs
docker compose logs -f backend     # backend logs
docker compose logs -f postgres    # database logs

# Stop services (preserve data)
docker compose stop

# Stop and remove containers (preserve data volume)
docker compose down

# Stop and remove containers + wipe database (⚠️ irreversible)
docker compose down -v

# Rebuild only one image
docker compose build backend
docker compose build frontend

# Rebuild and restart one service
docker compose up -d --build backend
```

### Data Persistence

Database data is stored in the Docker named volume `pgdata`. `docker compose down` does not delete it; only `docker compose down -v` wipes it.

### Building Images Standalone (without starting)

If you only want to produce images (e.g., to push to a registry):

```bash
# Run from the project root
docker build -f docker/Dockerfile.backend  -t graph-mng-backend:latest  .
docker build -f docker/Dockerfile.frontend -t graph-mng-frontend:latest .
```

## Configuration

### Backend (`application.yml` + profile key entries)

| Config Key | Default | Description |
|------------|---------|-------------|
| `server.port` | 8090 | Backend port |
| `server.servlet.context-path` | /api | API prefix |
| `spring.profiles.active` | local | Defaults to local dev; `prod` inside containers |
| `spring.datasource.*` (local) | 192.168.31.112/sqlgmngdb | Local datasource, see `application-local.yml` |
| `spring.datasource.*` (prod) | ${DB_HOST}/sqlgmngdb | Container datasource, env-injected, see `application-prod.yml` |
| `sqlg.jdbc.*` | same as primary datasource | sqlg graph datasource override (points to the same PG instance) |
| `spring.flyway.locations` | classpath:db/migration | Flyway migration script location |
| `mybatis.mapper-locations` | classpath:mapper/*.xml | MyBatis XML mapping |
| `mybatis.configuration.map-underscore-to-camel-case` | true | Automatic camelCase mapping |
| `sqlg.enabled` | true | Whether to initialize SqlgGraph (disabling doesn't affect the web layer) |
| `app.jwt.secret / expiration-ms` | — | JWT signing key / validity (default 24h) |

### Frontend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_TARGET` | http://localhost:8090 | Vite dev proxy / API backend address |
| `VITE_API_BASE` | /api | axios baseURL (overridable in production build) |

## Database Schema

Managed by Flyway. The first migration `V1__init_schema.sql` creates three tables:

| Table | Description |
|-------|-------------|
| `sys_user` | System user table (initial admin / 123456) |
| `sys_operation_log` | Operation log table |
| `sys_graph_connection` | Graph database connection config table |

Every table and every column has a `COMMENT`. New migration files follow the `V<n>__<desc>.sql` naming convention and are auto-applied by Flyway on startup. **Do not modify already-published migration files.**

## Implementation Progress

| Module | Backend | Frontend | Status |
|--------|---------|----------|--------|
| Connection Management | ✅ | ✅ | Fully implemented (CRUD + test + enable/disable + default + role-based visibility filtering) |
| Topology Browsing | ✅ | ✅ | Fully implemented (tree expansion + connection memory + refresh) |
| Vertex Type Management | ✅ | ✅ | Fully implemented (CRUD + clear vertices + related edges + table structure + examples) |
| Edge Type Management | ✅ | ✅ | Fully implemented (list + create + delete + clear + table structure + examples + direction preview) |
| Property Management | ✅ | ✅ | Fully implemented (property tree + connection memory + refresh + property CRUD + index management + UI metadata) |
| Vertex Data Management | ✅ | ✅ | Fully implemented (paginated query + property filtering + vertex detail + create/edit/delete + batch delete + clear + identifier support + related edges) |
| Edge Data Management | ✅ | ✅ | Fully implemented (paginated query + out/in vertex filtering + edge detail + create/edit/delete + batch delete + clear + export + vertex selector) |
| Graph Relationship Expansion | ✅ | ✅ | Fully implemented (connection selection + schema selection + vertex query modal + G6 graph + double-click expansion + multi-layout + type coloring + new-node highlighting) |
| Gremlin Console | ✅ | ✅ | Fully implemented (CodeMirror editor + selected execution + Ctrl+Enter shortcut + method autocompletion + security mode + five result views + query history + favorites management + examples) |
| Import/Export | ✅ | ✅ | Fully implemented (vertex/edge data CSV/JSON import-export + import preview + field mapping + error-row tracking + Topology export/import + overwrite mode) |
| Operation Log | ✅ | ✅ | Fully implemented (operation recording + paginated filtering + detail + dangerous-operation flagging + full coverage of Gremlin/import-export/vertex-edge-data/topology operations + JDBC masking) · grouped under "Audit Logs" |
| User Management | ✅ | ✅ | Fully implemented (user CRUD + role assignment + status enable/disable + password reset + permission query + login/operation records · grouped under "Users & Permissions") |
| Role Management | ✅ | ✅ | Fully implemented (role list + permission config: menu/operation/Gremlin/dangerous qualifications/visible connections + member management · grouped under "Users & Permissions") |
| Permission Overview | ✅ | ✅ | Fully implemented (view by user + view by role + reverse lookup by permission · user-level 7 tabs: summary/menu/operation/connections/Gremlin/dangerous qualifications/config check · role-level 8 tabs: +user members · permission-reverse-lookup modal includes three-layer dangerous-operation analysis) · grouped under "Users & Permissions" |
| Login Log | ✅ | ✅ | Fully implemented (login records + logout records + paginated filtering + client IP/UA · grouped under "Audit Logs") |

## API Overview

All APIs are prefixed with `/api`. Except `/auth/login`, all require the `Authorization: Bearer <token>` header.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | Login, returns JWT |
| POST | `/auth/logout` | Logout |
| GET | `/auth/info` | Get current user info |
| GET | `/connection` | List all connections (supports keyword search) |
| POST | `/connection` | Create a connection |
| PUT | `/connection/{id}` | Edit a connection (empty password leaves it unchanged) |
| DELETE | `/connection/{id}` | Delete a connection |
| POST | `/connection/test` | Test a connection (during form fill, not persisted) |
| POST | `/connection/{id}/test` | Test a saved connection |
| PUT | `/connection/{id}/status` | Enable/disable `{status: 0\|1}` |
| PUT | `/connection/{id}/default` | Set as default connection |
| GET | `/topology/connections` | List connections available for topology browsing + user's last selection |
| GET | `/topology/{connectionId}` | Get the full topology of a connection (Schema / VertexLabel / EdgeLabel / properties / indexes) |
| POST | `/topology/{connectionId}/refresh` | Clear that connection's topology cache; next query reloads |
| PUT | `/topology/active-connection` | Remember the connection selected on the topology page `{connectionId: Long\|null}` |
| GET | `/vertex-type/connections` | List available connections + user's last selection |
| PUT | `/vertex-type/active-connection` | Remember the user-selected connection `{connectionId: Long\|null}` |
| GET | `/vertex-type/{connectionId}` | List all VertexLabels under that connection |
| GET | `/vertex-type/{connectionId}/{schema}/{label}` | Get a single VertexLabel detail (properties / indexes / identifiers) |
| POST | `/vertex-type/{connectionId}` | Create a VertexLabel |
| PUT | `/vertex-type/{connectionId}` | Edit a VertexLabel (adds missing properties) |
| DELETE | `/vertex-type/{connectionId}` | Delete a VertexLabel and its underlying V_XXX table `{schema, label}` |
| POST | `/vertex-type/{connectionId}/clear-vertices` | Clear only vertex data, keeping the VertexLabel definition |
| GET | `/vertex-type/{connectionId}/{schema}/{label}/edges` | View related edge types (in-edges + out-edges) |
| GET | `/vertex-type/{connectionId}/{schema}/{label}/table-schema` | View the underlying physical table structure |
| GET | `/vertex-type/gremlin-examples/{schema}/{label}` | Generate example Gremlin queries |
| GET | `/vertex-type/sql-examples/{schema}/{label}` | Generate example SQL queries |
| GET | `/edge-type/connections` | List available connections + user's last selection |
| PUT | `/edge-type/active-connection` | Remember the user-selected connection `{connectionId: Long\|null}` |
| GET | `/edge-type/{connectionId}` | List all EdgeLabels under that connection |
| GET | `/edge-type/{connectionId}/{schema}/{label}` | Get a single EdgeLabel detail (properties / indexes / out-in vertex types) |
| POST | `/edge-type/{connectionId}` | Create an EdgeLabel (must specify out/in vertex types) |
| DELETE | `/edge-type/{connectionId}` | Delete an EdgeLabel and its underlying E_XXX table `{schema, label}` |
| POST | `/edge-type/{connectionId}/clear-edges` | Clear only edge data, keeping the EdgeLabel definition |
| GET | `/edge-type/{connectionId}/{schema}/{label}/table-schema` | View the underlying physical table structure |
| GET | `/edge-type/{connectionId}/vertex-labels` | List all VertexLabels (for the new-edge form to select out/in vertices) |
| GET | `/edge-type/gremlin-examples/{schema}/{label}` | Generate example Gremlin queries |
| GET | `/edge-type/sql-examples/{schema}/{label}` | Generate example SQL queries |

Unified response format:
```json
{ "code": 0, "message": "success", "data": {...} }
```
`code = 0` means success; non-zero means a business error (`code = 400` parameter error, `code = 401` not logged in, `code = 500` service exception).

## Development Guide

For more detailed project structure, tech-stack notes, development conventions, and known pitfalls, read **[AGENTS.md](./AGENTS.md)**.

### Startup (tmux background)

```bash
# Backend
tmux new-session -d -s backend -c backend
tmux send-keys -t backend "mvn spring-boot:run" Enter

# Frontend
tmux new-session -d -s frontend -c frontend
tmux send-keys -t frontend "npm run dev" Enter

# View logs
tmux attach -t backend    # Ctrl+B D to detach

# Stop
tmux kill-session -t backend
```

### Project Conventions

- **Package prefix**: `com.trs`; business modules go in `com.trs.modules.<name>/`
- **Unified responses**: Controllers return `Result<T>`; errors throw `IllegalArgumentException` converted to 400
- **Field mapping**: DB `snake_case` ↔ Java `camelCase`; **hand-written aliases in MyBatis XML are forbidden**
- **API calls**: The frontend must go through `src/api/client.js`; **raw `fetch` / raw `axios` are forbidden**
- **Styling**: Use only Tailwind utility classes; do not write custom CSS
- **Icons**: `lucide-react`, imported on demand
- **Type safety**: Do not use `@ts-ignore` / `as any` / `@SuppressWarnings` to mask errors
- **Comments**: Code should be self-explanatory; DDL must have COMMENTs; only add comments for complex logic (algorithms/regex/security/magic numbers)

## License

MIT
