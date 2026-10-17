"""Java 后端 REST 客户端：Agent 调用 Java 的唯一网络边界

职责：
- 统一拼接 base_url（来自 Settings）
- 注入机器身份头 X-API-Key（Java 端需配套实现校验；未实现前留空则不带）
- 透传真实操作人 X-Acting-User（解决"API Key 机器身份 vs 真实用户"的问题）
- 解析 Java 统一响应 ResponseResult{code,message,data}，非 200 抛 BackendError
"""
from __future__ import annotations

import httpx

from app.core.config import settings


class BackendError(Exception):
    """Java 后端返回的业务/HTTP 错误"""

    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def _build_headers(acting_user: int | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if settings.omniagent_api_key:
        headers["X-API-Key"] = settings.omniagent_api_key
    # 真实操作人：Agent 用机器密钥调 Java 时，把"当前登录用户 id"带给 Java
    if acting_user is not None:
        headers["X-Acting-User"] = str(acting_user)
    return headers


async def request(
    method: str,
    path: str,
    *,
    params: dict | None = None,
    json: dict | None = None,
    acting_user: int | None = None,
) -> dict:
    headers = _build_headers(acting_user)
    async with httpx.AsyncClient(
        base_url=settings.omniagent_api_base_url, timeout=10.0
    ) as client:
        resp = await client.request(
            method, path, params=params, json=json, headers=headers
        )
        try:
            body = resp.json()
        except Exception:
            raise BackendError(
                f"Java 后端返回非 JSON 响应 (HTTP {resp.status_code})", resp.status_code
            )

        # Java 统一响应体：{code, message, data}
        code = body.get("code") if isinstance(body, dict) else None
        if resp.status_code >= 400 or (code not in (None, "200")):
            msg = body.get("message") if isinstance(body, dict) else resp.text
            raise BackendError(msg or f"HTTP {resp.status_code}", resp.status_code)

        return body.get("data")


# ===================== 业务接口封装 =====================


async def get_dict_all() -> dict:
    """拉取全部字典（分类/状态/优先级）"""
    return await request("GET", "/api/dict/all")


async def page_tickets(
    *,
    keyword: str = "",
    status_id: int | None = None,
    priority_id: int | None = None,
    category_id: int | None = None,
    page: int = 1,
    page_size: int = 10,
    acting_user: int | None = None,
) -> dict:
    """分页查询工单（参数对齐 Java TicketQuery）"""
    params: dict = {"pageNum": page, "pageSize": page_size}
    if keyword:
        params["keyword"] = keyword
    if status_id is not None:
        params["statusId"] = status_id
    if priority_id is not None:
        params["priorityId"] = priority_id
    if category_id is not None:
        params["categoryId"] = category_id
    return await request("GET", "/api/tickets/page", params=params, acting_user=acting_user)


async def create_ticket(payload: dict, *, acting_user: int | None = None) -> dict:
    """创建工单（payload 对齐 Java TicketCreateDTO）"""
    return await request("POST", "/api/tickets", json=payload, acting_user=acting_user)
