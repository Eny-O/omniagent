# OmniAgent 数据库初始化

用于从零拉起项目的 SQL 脚本。**只含表结构与最小种子数据**，不含演示工单、会话状态与知识库语料。

| 文件 | 内容 |
| --- | --- |
| `01_schema.sql` | 13 张业务表、索引、`ticket_no_seq` 序列、pgvector 扩展 |
| `02_init_data.sql` | 7 状态 / 3 优先级 / 10 分类 / 5 来源 / 6 部门 / 5 个登录账号 |

两个脚本都**可重复执行**（`IF NOT EXISTS` / `ON CONFLICT DO NOTHING`）。

## 环境要求

- Docker（推荐用仓库根目录的 `docker-compose.yml` 一键起库）
- PostgreSQL 15+（项目实测 17.11）
- [`pgvector`](https://github.com/pgvector/pgvector) 扩展：`01_schema.sql` 会执行 `CREATE EXTENSION IF NOT EXISTS vector`，官方镜像已内置。

## 两种启动方式

compose 只有一份文件，靠命令指定服务范围区分两种模式（完整说明见仓库根目录 `README.md`）：

| 命令 | 起什么 | 适用场景 |
| --- | --- | --- |
| `docker compose up -d postgres` | 仅数据库 | 日常开发：Java / Vue / Agent 本机跑，保留热更新与 devtools |
| `docker compose up -d` | 数据库 + 三端 | 拉取项目后一键体验完整功能 |

在**仓库根目录**执行。postgres 服务使用 `pgvector/pgvector:pg17`，默认库 `omniagent` / 用户 `postgres` / 密码 `postgres` / 宿主端口 `5432`，与 Java `spring.datasource` 的默认值一致。

脚本会把 `sql/` 挂到 `/docker-entrypoint-initdb.d/`，**数据卷为空的首次启动**按文件名顺序自动执行 `01_schema.sql`、`02_init_data.sql`，下面的手动步骤可以跳过。全量编排时 Java 与 Agent 的数据源由 compose 依据 `POSTGRES_*` 自动注入（容器内用服务名 `postgres`），无需手填。

覆盖默认参数（写进根目录 `.env`，或直接前置到命令前）：

```bash
POSTGRES_PASSWORD=<自定义密码> POSTGRES_PORT=5433 docker compose up -d postgres
```

改了用户名 / 密码，**全量编排**会自动同步到三端；但**本机跑三端**时需自行设置 Java 的 `SPRING_DATASOURCE_PASSWORD`（或按 `application.yml.example` 建本地配置）与 Agent `.env` 的 `PG_DSN`。`POSTGRES_PORT` 只改宿主映射端口，容器内始终是 5432。

> 初始化脚本只在新建数据卷时执行；`docker compose down -v` 会删除卷内数据，重建前请先备份。

## 执行顺序（手动初始化，不使用 Docker 时）

命令行：

```bash
psql -U postgres -d omniagent -f sql/01_schema.sql
psql -U postgres -d omniagent -f sql/02_init_data.sql
```

DataGrip：打开 `01_schema.sql`、选择目标数据源后全选执行，再对 `02_init_data.sql` 重复一次。**必须先跑结构，再跑种子**。

## 默认账号

密码统一为 `123456`（BCrypt 哈希写入 `users.password_hash`）。

| 用户名 | 角色 | 部门 | 说明 |
| --- | --- | --- | --- |
| `admin` | ADMIN | IT | 全量数据范围，可硬删工单 |
| `zhangsan` | REPORTER | HQ | 报障人 |
| `lisi` | HANDLER | IT_OPS | 处理人 |
| `wangwu` | SUPERVISOR | HR | 主管，可派单 |
| `zhaoliu` | REPORTER | HQ | 报障人 |

> 这是一份演示/开发用账号与弱口令，部署到任何非本机环境前请改密并替换哈希。

## 几个约定

- **状态字典是 7 行**，`id` 与 `TicketStatusEnum.dbId` 严格对应：`DRAFT=1`、`PENDING=2`、`PROCESSING=3`、`RESOLVED=4`、`CLOSED=5`、`CANCELLED=6`、`TO_ASSIGN=7`。草稿由创建者 `submitTicket()` 提交后进入 `TO_ASSIGN`，不能通过状态接口直接推进到 `PENDING`。
- **角色只有 4 个业务角色**：`REPORTER` / `HANDLER` / `SUPERVISOR` / `ADMIN`。AGENT 是智能辅助层，不是业务角色，也不进 `users.role`。后端授权（DataScope + Capability）只针对业务角色；角色为空会 fail-closed 拒绝访问。
- **`ticket_no_seq` 必须存在**。工单号由 PostgreSQL 序列 `nextval` 原子取号（格式 `TKT-yyyyMMdd-NNN`），Java 端 `TicketNoGenerator` 直接依赖它，缺序列会导致建单失败。
- **LangGraph 的 `checkpoint*` 4 张表不用建**。Agent 启动时 `AsyncPostgresSaver.setup()` 会自动创建并维护，属于运行时状态，不放进初始化脚本。

## 知识库语料（需自行灌入）

`01_schema.sql` 只建 `kb_docs` / `kb_chunks` 两张空表，**不包含任何语料与向量**。

- 入库入口在 Agent 端 `omniagent-agent/OmniAgent/app/kb/ingestion.py`：文档切分 → 生成 embedding → 幂等写入两层表（`content_hash` 未变化则跳过）。语料文件需自行准备。
- `kb_chunks.embedding` 固定为 `vector(1024)`，须与 `app/core/config.py` 的 `embedding_dim` 及所用 embedding 模型一致；改维度要同步改列定义并重跑入库。
- 当前**不建** ANN 向量索引（ivfflat / HNSW），小数据量下走精确扫描；数据量上来后可自行补 HNSW 索引。
- 未灌入语料时，知识库问答检索不到有效片段，会返回低分偏转结论而不是硬答；这是预期行为，不是故障。工单类操作（查单、建单、派单等）不依赖 KB，可正常使用。

## 应用侧配置

- **全量编排（容器内）**：`docker-compose.yml` 依据 `POSTGRES_*` 自动注入 Java 的 `SPRING_DATASOURCE_*` 与 Agent 的 `PG_DSN`，容器间用服务名 `postgres` / `java` 互访；`JWT_SECRET` 同时注入两端。
- **本机开发（只起 DB）**：Java 读 `SPRING_DATASOURCE_*` 环境变量（`application.yml` 只提供 `localhost:5432` 的 URL 占位与空口令默认值）；Agent 读 `omniagent-agent/OmniAgent/.env` 的 `PG_DSN`（`localhost:5432`）与 `omniagent_api_base_url`（`localhost:8080`）。compose 的容器服务名不会影响本机进程。
- Agent 依赖清单：`omniagent-agent/OmniAgent/requirements.txt`（Python 3.12）。
- **两端 JWT 密钥必须一致**：Java `JwtUtil` 签发/验签、Agent `app/utils/jwt.py` 验签前端 Bearer 都用它；请用强随机值覆盖默认值，且不要把含密钥的配置提交进仓库。
- 当前认证为无状态 JWT（`Authorization: Bearer`），不使用 Session/Cookie 或 `X-API-Key`。`config.py` 的 `omniagent_api_key` 是遗留死配置，无需设置；`application.yml` 已不含明文凭证，本机真实口令放 `application-local.yml`（不入库）。
