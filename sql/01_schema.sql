-- ============================================================
-- OmniAgent 智能工单助手 - 数据库表结构
-- 适用：PostgreSQL 15+ / pgvector
-- 可重复执行：建表、建序列、建索引均使用 IF NOT EXISTS
-- 覆盖范围：工单业务表 + 知识库表；不含 LangGraph checkpoint 表
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. 组织架构
-- ============================================================

CREATE TABLE IF NOT EXISTS departments (
    id           BIGSERIAL PRIMARY KEY,
    dept_code    VARCHAR(64)  NOT NULL UNIQUE,
    dept_name    VARCHAR(128) NOT NULL,
    parent_id    BIGINT REFERENCES departments(id),
    location     VARCHAR(255),
    sort_order   INT DEFAULT 0,
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE departments IS '部门表，支持树形结构';
COMMENT ON COLUMN departments.location IS '部门位置，作为该部门工单位置的默认取值';

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name     VARCHAR(64),
    email         VARCHAR(128),
    phone         VARCHAR(32),
    dept_id       BIGINT REFERENCES departments(id),
    status        SMALLINT DEFAULT 1,
    role          VARCHAR(32) DEFAULT 'REPORTER',
    created_at    TIMESTAMP DEFAULT NOW(),
    updated_at    TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE users IS '系统用户';
COMMENT ON COLUMN users.status IS '1=启用, 0=禁用';
COMMENT ON COLUMN users.role IS '业务角色编码：REPORTER / HANDLER / SUPERVISOR / ADMIN';

-- ============================================================
-- 2. 工单字典
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket_categories (
    id             SERIAL PRIMARY KEY,
    category_code  VARCHAR(64)  NOT NULL UNIQUE,
    category_name  VARCHAR(128) NOT NULL,
    parent_id      INT REFERENCES ticket_categories(id),
    sort_order     INT DEFAULT 0,
    created_at     TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE ticket_categories IS '工单分类，支持树形结构';

CREATE TABLE IF NOT EXISTS ticket_statuses (
    id          SERIAL PRIMARY KEY,
    status_code VARCHAR(32) NOT NULL UNIQUE,
    status_name VARCHAR(64) NOT NULL,
    is_terminal BOOLEAN DEFAULT FALSE,
    sort_order  INT DEFAULT 0
);

COMMENT ON TABLE ticket_statuses IS '工单状态字典';
COMMENT ON COLUMN ticket_statuses.is_terminal IS '是否为终态';

CREATE TABLE IF NOT EXISTS ticket_priorities (
    id                   SERIAL PRIMARY KEY,
    priority_code        VARCHAR(32) NOT NULL UNIQUE,
    priority_name        VARCHAR(64) NOT NULL,
    response_sla_minutes INT,
    resolve_sla_minutes  INT,
    sort_order           INT DEFAULT 0
);

COMMENT ON TABLE ticket_priorities IS '工单优先级字典，含 SLA 指标';

CREATE TABLE IF NOT EXISTS ticket_sources (
    id           SERIAL PRIMARY KEY,
    source_code  VARCHAR(32) NOT NULL UNIQUE,
    source_name  VARCHAR(64) NOT NULL,
    sort_order   INT DEFAULT 0
);

COMMENT ON TABLE ticket_sources IS '工单来源字典';

-- ============================================================
-- 3. 工单主表
-- ============================================================

CREATE TABLE IF NOT EXISTS tickets (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_no           VARCHAR(64)  NOT NULL UNIQUE,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    category_id         INT REFERENCES ticket_categories(id),
    priority_id         INT REFERENCES ticket_priorities(id),
    status_id           INT REFERENCES ticket_statuses(id),
    creator_id          BIGINT REFERENCES users(id),
    current_assignee_id BIGINT REFERENCES users(id),
    source              VARCHAR(32) DEFAULT 'WEB',
    contact_phone       VARCHAR(32),
    contact_email       VARCHAR(128),
    location            VARCHAR(255),
    dept_id             BIGINT,
    expected_resolve_at TIMESTAMP,
    resolved_at         TIMESTAMP,
    closed_at           TIMESTAMP,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE tickets IS '工单主表';
COMMENT ON COLUMN tickets.source IS '来源编码，取值见 ticket_sources.source_code';
COMMENT ON COLUMN tickets.dept_id IS '工单受理部门 ID，用于 SUPERVISOR 数据范围过滤';

-- ============================================================
-- 4. 工单关联表
-- ============================================================

CREATE TABLE IF NOT EXISTS ticket_assignments (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    assignee_id  BIGINT REFERENCES users(id),
    assignor_id  BIGINT REFERENCES users(id),
    assigned_at  TIMESTAMP DEFAULT NOW(),
    is_current   BOOLEAN DEFAULT TRUE,
    remark       VARCHAR(500)
);

COMMENT ON TABLE ticket_assignments IS '工单派单记录';
COMMENT ON COLUMN ticket_assignments.is_current IS '是否为当前有效指派';

CREATE TABLE IF NOT EXISTS ticket_comments (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id    BIGINT REFERENCES users(id),
    author_type  VARCHAR(16) DEFAULT 'USER',
    content      TEXT NOT NULL,
    is_internal  BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE ticket_comments IS '工单评论与内部备注';
COMMENT ON COLUMN ticket_comments.author_type IS 'REPORTER / OPERATOR / SYSTEM';
COMMENT ON COLUMN ticket_comments.is_internal IS 'TRUE=仅处理角色可见';

CREATE TABLE IF NOT EXISTS ticket_attachments (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT REFERENCES tickets(id) ON DELETE CASCADE,
    comment_id   BIGINT REFERENCES ticket_comments(id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    file_size    BIGINT,
    mime_type    VARCHAR(128),
    uploaded_by  BIGINT REFERENCES users(id),
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE ticket_attachments IS '工单附件';

CREATE TABLE IF NOT EXISTS ticket_history (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    operator_id  BIGINT REFERENCES users(id),
    operation    VARCHAR(64) NOT NULL,
    field_name   VARCHAR(64),
    old_value    TEXT,
    new_value    TEXT,
    remark       VARCHAR(500),
    created_at   TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE ticket_history IS '工单操作审计历史';

-- ============================================================
-- 5. 知识库
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_docs (
    doc_id       TEXT PRIMARY KEY,
    title        TEXT        NOT NULL,
    source       TEXT        NOT NULL,
    content_hash TEXT        NOT NULL,
    meta         JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE kb_docs IS '知识库文档元数据';
COMMENT ON COLUMN kb_docs.content_hash IS '内容指纹，入库幂等比对键';

CREATE TABLE IF NOT EXISTS kb_chunks (
    chunk_id    TEXT PRIMARY KEY,
    doc_id      TEXT        NOT NULL REFERENCES kb_docs(doc_id) ON DELETE CASCADE,
    chunk_index INT         NOT NULL,
    content     TEXT        NOT NULL,
    embedding   vector(1024),
    meta        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE kb_chunks IS '知识库文本切块与向量';
COMMENT ON COLUMN kb_chunks.embedding IS '1024 维向量，与 Agent 配置 embedding_dim 一致';

-- ============================================================
-- 6. 工单号序列
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS ticket_no_seq START WITH 1000;

-- ============================================================
-- 7. 索引
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_tickets_creator_id  ON tickets(creator_id);
CREATE INDEX IF NOT EXISTS idx_tickets_assignee_id ON tickets(current_assignee_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status_id   ON tickets(status_id);
CREATE INDEX IF NOT EXISTS idx_tickets_priority_id ON tickets(priority_id);
CREATE INDEX IF NOT EXISTS idx_tickets_category_id ON tickets(category_id);
CREATE INDEX IF NOT EXISTS idx_tickets_created_at  ON tickets(created_at);

CREATE INDEX IF NOT EXISTS idx_ticket_assignments_ticket_id ON ticket_assignments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_id    ON ticket_comments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_attachments_ticket_id ON ticket_attachments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_history_ticket_id     ON ticket_history(ticket_id);

CREATE INDEX IF NOT EXISTS ix_kb_chunks_doc ON kb_chunks(doc_id);
