"""知识库文本向量化（阿里云百炼 qwen3.7-text-embedding）。"""
from __future__ import annotations

import httpx

from app.core.config import settings

# 百炼 OpenAI 兼容 embeddings 端点（与 OpenAI 接口同构，用 httpx 直调即可）
_DASHSCOPE_EMBED_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"


async def embed_texts(texts: list[str]) -> list[list[float]]:
    """批量将文本转成向量。返回与输入等长的向量列表。

    Args:
        texts: 一批文本（一次别太多，数十~上百条为宜，避免请求体过大）
    Returns:
        每条文本对应的向量（list[float]），维度 = settings.embedding_dim
    """
    if not texts:
        return []

    if not settings.dashscope_api_key:
        raise RuntimeError("dashscope_api_key 为空，请在 .env 配置 DASHSCOPE_API_KEY")

    headers = {
        "Authorization": f"Bearer {settings.dashscope_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.embedding_model,          # qwen3.7-text-embedding
        "input": texts,
        "dimensions": settings.embedding_dim,        # 256~2560 自定义维度，须与向量列一致
        "encoding_format": "float",
    }

    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(_DASHSCOPE_EMBED_URL, headers=headers, json=payload)
        resp.raise_for_status()
        data = resp.json()

    # 百炼返回 data 按 index 排序，保证和输入顺序一致
    items = sorted(data["data"], key=lambda x: x["index"])
    return [item["embedding"] for item in items]


async def embed_text(text: str) -> list[float]:
    """单条文本向量化（embed_texts 的便捷封装）。"""
    return (await embed_texts([text]))[0]
