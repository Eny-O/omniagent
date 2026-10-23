"""知识库检索：问题向量化 → pgvector 余弦召回 top-k → （预留）rerank。

偏转（deflection）靠它：Agent 建单前先 search_kb，命中相关片段就直接答。
"""
from __future__ import annotations

from app.core.config import settings
from app.core.database import get_pool
from app.kb.embedding import embed_text


def rerank(query: str, chunks: list[dict]) -> list[dict]:
    """rerank 扩展点（当前透传）。

    为"预留"而留的钩子：将来接 bge-reranker / 百炼 rerank 时，
    只改这个函数体（对 chunks 重新打分排序），search_kb 调用方完全不动。
    由 config.kb_rerank_enabled 控制是否启用。
    """
    return chunks


async def search_kb(query: str, top_k: int | None = None) -> list[dict]:
    """检索知识库，返回与 query 最相关的 top-k 切块。

    Returns: 每项含
        chunk_id, doc_id, chunk_index, content,
        title(文档标题), source(来源), score(余弦相似度 0~1，越大越相关)
    """
    top_k = top_k or settings.kb_top_k
    vec = await embed_text(query)
    vec_str = "[" + ",".join(str(x) for x in vec) + "]"

    pool = get_pool()
    rows = await pool.fetch(
        """SELECT c.chunk_id, c.doc_id, c.chunk_index, c.content,
                  d.title, d.source,
                  1 - (c.embedding <=> $1::vector) AS score
           FROM kb_chunks c
           JOIN kb_docs d ON d.doc_id = c.doc_id
           ORDER BY c.embedding <=> $1::vector
           LIMIT $2""",
        vec_str, top_k,
    )

    chunks = [dict(r) for r in rows]
    chunks = rerank(query, chunks) if settings.kb_rerank_enabled else chunks
    return chunks
