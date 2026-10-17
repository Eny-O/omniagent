"""工单字典解析服务：把 Agent 收集到的中文标签转成 Java 需要的 id

依赖 repositories/backend_client 拉取字典。缓存到进程内存，
首次使用前需调用 load_dicts()（建议在 FastAPI lifespan 启动时预热）。
"""
from __future__ import annotations

from app.repositories import backend_client

# 进程内缓存：categories / statuses / priorities 均为后端 VO 列表
_cache: dict = {"categories": None, "statuses": None, "priorities": None}


async def load_dicts() -> None:
    """从 Java 拉取全部字典并缓存（幂等，可重复调用）"""
    data = await backend_client.get_dict_all()
    _cache["categories"] = data.get("categories") or []
    _cache["statuses"] = data.get("statuses") or []
    _cache["priorities"] = data.get("priorities") or []


async def _ensure_loaded() -> None:
    if _cache["categories"] is None:
        await load_dicts()


async def resolve_category_id(label: str) -> int | None:
    """分类标签 -> id（如 '硬件故障' -> 1）。精确匹配。"""
    await _ensure_loaded()
    for item in _cache["categories"]:
        if item.get("label") == label:
            return item.get("value")
    return None


async def resolve_priority_id(label: str) -> int | None:
    """优先级标签 -> id（如 '高' -> 3）。"""
    await _ensure_loaded()
    for item in _cache["priorities"]:
        if item.get("label") == label:
            return item.get("value")
    return None


async def resolve_status_id(label: str) -> int | None:
    """状态标签 -> id（如 '待处理' -> 2）。"""
    await _ensure_loaded()
    for item in _cache["statuses"]:
        if item.get("label") == label:
            return item.get("value")
    return None


def category_options() -> list:
    return _cache["categories"] or []


def priority_options() -> list:
    return _cache["priorities"] or []


def status_options() -> list:
    return _cache["statuses"] or []
