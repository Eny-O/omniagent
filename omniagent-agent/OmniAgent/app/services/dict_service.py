"""工单字典解析服务：把 Agent 收集到的中文标签转成 Java 需要的 id

依赖 repositories/backend_client 拉取字典。缓存到进程内存，
首次使用前需调用 load_dicts() 在 FastAPI lifespan 启动时预热
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


async def resolve_category(arg) -> int | None:
    """分类：接受中文标签或数字 id，统一返回合法 id；无法识别返回 None。"""
    await _ensure_loaded()
    if arg is None:
        return None
    if isinstance(arg, int):
        ids = {i.get("value") for i in _cache["categories"] or []}
        return arg if arg in ids else None
    return await resolve_category_id(str(arg))


async def resolve_priority(arg) -> int | None:
    """优先级：接受中文标签或数字 id，统一返回合法 id；无法识别返回 None。"""
    await _ensure_loaded()
    if arg is None:
        return None
    if isinstance(arg, int):
        ids = {i.get("value") for i in _cache["priorities"] or []}
        return arg if arg in ids else None
    return await resolve_priority_id(str(arg))


async def resolve_status(arg) -> int | None:
    """状态：接受中文标签或数字 id，统一返回合法 id；无法识别返回 None。"""
    await _ensure_loaded()
    if arg is None:
        return None
    if isinstance(arg, int):
        ids = {i.get("value") for i in _cache["statuses"] or []}
        return arg if arg in ids else None
    return await resolve_status_id(str(arg))


def category_options() -> list:
    return _cache["categories"] or []


def priority_options() -> list:
    return _cache["priorities"] or []


def status_options() -> list:
    return _cache["statuses"] or []


def to_prompt_text() -> str:
    """将预热好的字典渲染成 LLM 可读的映射文本，供 system prompt 动态注入。

    缓存为空（尚未 load_dicts）时返回空串，调用方可据此决定是否拼接。
    返回的每一行形如「标签=数字id」，让 LLM 在调用工具时优先使用数字 id。
    """
    def fmt(items):
        return "，".join(
            f"{i.get('label')}={i.get('value')}"
            for i in (items or [])
            if i.get("label") is not None
        )

    cats = fmt(_cache["categories"])
    pris = fmt(_cache["priorities"])
    stats = fmt(_cache["statuses"])
    if not (cats or pris or stats):
        return ""
    lines = ["工单字典映射（用户用中文描述时，请使用对应的数字 id 传参）："]
    if cats:
        lines.append(f"- 分类: {cats}")
    if pris:
        lines.append(f"- 优先级: {pris}")
    if stats:
        lines.append(f"- 状态: {stats}")
    return "\n".join(lines)
