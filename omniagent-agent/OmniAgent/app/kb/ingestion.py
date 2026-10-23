"""知识库入库 pipeline：loader / splitter / embed / upsert 四段解耦，支持幂等重跑。
幂等：
  - doc_id 由 source+title 稳定派生
  - content_hash 比对，内容未变整篇跳过
  - chunk_id 稳定主键 + ON CONFLICT，重跑不重复
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

from app.core.config import settings
from app.core.database import get_pool
from app.kb.embedding import embed_texts


@dataclass
class KBDocument:
    title: str
    content: str
    source: str            # 来源标识：文件路径 / URL / 'generated' / ...
    meta: dict = field(default_factory=dict)


# ---------- loader（换文档源只动这里）----------

def load_text(title: str, content: str, source: str = "generated") -> KBDocument:
    """AI 生成或内存中的文本。"""
    return KBDocument(title=title, content=content, source=source)


def load_file(path: str, source: str | None = None) -> KBDocument:
    """读取本地 .txt / .md 文件。"""
    p = Path(path)
    content = p.read_text(encoding="utf-8")
    title = p.stem
    source = source or f"file://{p.resolve()}"
    return KBDocument(title=title, content=content, source=source)


# ---------- splitter（切块，尽量在边界断开）----------

def split_text(text: str, chunk_size: int, overlap: int) -> list[str]:
    """滑动窗口切块：窗口大小 chunk_size，窗口间重叠 overlap，
    优先在 '\\n\\n' / '。' / '. ' 处断开，避免切到句子中间。"""
    text = text.strip()
    if not text:
        return []
    step = max(1, chunk_size - overlap)
    n = len(text)
    chunks: list[str] = []
    start = 0
    while start < n:
        end = min(start + chunk_size, n)
        piece = text[start:end]
        if end < n:
            cut = max(piece.rfind("\n\n"), piece.rfind("。"), piece.rfind(". "))
            if cut > step // 2:                       # 只在该断点足够靠后时才切，避免太碎
                end = start + cut + (2 if piece[cut:cut + 2] == "\n\n" else 1)
                piece = text[start:end]
        piece = piece.strip()
        if piece:
            chunks.append(piece)
        if end >= n:
            break
        start = end - overlap
    return chunks


# ---------- 稳定标识 ----------

def _doc_id(source: str, title: str) -> str:
    return hashlib.sha256(f"{source}|{title}".encode("utf-8")).hexdigest()[:24]


def _content_hash(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


# ---------- upsert（幂等核心）----------

async def ingest_document(doc: KBDocument) -> dict:
    pool = get_pool()
    doc_id = _doc_id(doc.source, doc.title)
    content_hash = _content_hash(doc.content)
    meta_json = json.dumps(doc.meta or {})

    # 内容未变 → 跳过（不重复调 embedding、不重复写）
    row = await pool.fetchrow("SELECT content_hash FROM kb_docs WHERE doc_id=$1", doc_id)
    if row is not None and row["content_hash"] == content_hash:
        return {"doc_id": doc_id, "status": "skipped", "chunks": 0}

    # 切块 + 批量向量化
    chunks = split_text(doc.content, settings.kb_chunk_size, settings.kb_chunk_overlap)
    vectors = await embed_texts(chunks)

    # 事务写入：upsert 文档 → 删旧块 → 插新块（稳定 chunk_id 保证幂等）
    async with pool.acquire() as conn:
        async with conn.transaction():
            await conn.execute(
                """INSERT INTO kb_docs (doc_id, title, source, content_hash, meta, updated_at)
                   VALUES ($1,$2,$3,$4,$5::jsonb, now())
                   ON CONFLICT (doc_id) DO UPDATE
                     SET title=EXCLUDED.title, content_hash=EXCLUDED.content_hash,
                         meta=EXCLUDED.meta, updated_at=now()""",
                doc_id, doc.title, doc.source, content_hash, meta_json,
            )
            await conn.execute("DELETE FROM kb_chunks WHERE doc_id=$1", doc_id)
            for idx, (chunk, vec) in enumerate(zip(chunks, vectors)):
                chunk_id = f"{doc_id}#{idx}"
                vec_str = "[" + ",".join(str(x) for x in vec) + "]"   # pgvector 文本格式
                await conn.execute(
                    """INSERT INTO kb_chunks (chunk_id, doc_id, chunk_index, content, embedding, meta)
                       VALUES ($1,$2,$3,$4,$5::vector,$6::jsonb)
                       ON CONFLICT (chunk_id) DO UPDATE
                         SET content=EXCLUDED.content, embedding=EXCLUDED.embedding,
                             chunk_index=EXCLUDED.chunk_index""",
                    chunk_id, doc_id, idx, chunk, vec_str, meta_json,
                )
    return {"doc_id": doc_id, "status": "upserted", "chunks": len(chunks)}


async def ingest_documents(docs: list[KBDocument]) -> list[dict]:
    return [await ingest_document(d) for d in docs]


async def delete_document(source: str, title: str) -> None:
    """删除整篇文档（kb_chunks 因 ON DELETE CASCADE 自动跟着删）。"""
    doc_id = _doc_id(source, title)
    pool = get_pool()
    await pool.execute("DELETE FROM kb_docs WHERE doc_id=$1", doc_id)
