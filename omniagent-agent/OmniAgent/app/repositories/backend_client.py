"""Java 后端 REST 客户端：Agent 调用 Java 的唯一网络边界

职责：
- 统一拼接 base_url（来自 Settings）
- 注入机器身份头 X-API-Key（Java 端 MachineIdentityAuthenticationFilter + ActingContextInterceptor 已实现校验）
- 透传真实操作人 X-Acting-User（解决"API Key 机器身份 vs 真实用户"的问题）
- 启动期系统等内部调用使用 SYSTEM_ACTING_ROLE，避免冒充具体终端用户
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


# Agent 服务自身的「系统身份」：用于启动期预热等不代指具体终端用户的内部调用。
# 选 ADMIN 的原因：字典为全量共享参考数据、不按角色过滤；ADMIN→ALL 数据范围且不依赖
# user_id（仅 SELF 范围才需要），因此传 role=ADMIN + acting_user=None 即可过 Java 拦截器，
# 又不耦合到某个具体业务管理员账号（避免"冒充真人"）。
SYSTEM_ACTING_ROLE = "ADMIN"


def _build_headers(acting_user: int | None = None, acting_role: str | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if settings.omniagent_api_key:
        headers["X-API-Key"] = settings.omniagent_api_key
        if acting_role:
            headers["X-Acting-Role"] = acting_role
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
    acting_role: str | None = None,
) -> dict:
    headers = _build_headers(acting_user, acting_role)
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
    """拉取全部字典（分类/状态/优先级）。

    启动期预热调用，此时没有终端用户上下文，使用 Agent 自身系统身份
    (SYSTEM_ACTING_ROLE) 通过 Java 机器身份鉴权，而非冒充某位具体用户。
    """
    return await request("GET", "/api/dict/all", acting_role=SYSTEM_ACTING_ROLE)


async def page_tickets(
    *,
    keyword: str = "",
    status_id: int | None = None,
    priority_id: int | None = None,
    category_id: int | None = None,
    page: int = 1,
    page_size: int = 10,
    acting_user: int | None = None,
    acting_role: str | None = None,
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
    return await request("GET", "/api/tickets/page", params=params, acting_user=acting_user, acting_role=acting_role)


async def create_ticket(payload: dict, *, acting_user: int | None = None, acting_role: str | None = None) -> dict:
    """创建工单（payload 对齐 Java TicketCreateDTO）"""
    return await request("POST", "/api/tickets", json=payload, acting_user=acting_user, acting_role=acting_role)


async def get_ticket(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """查询工单详情（GET /api/tickets/{id}）"""
    return await request("GET", f"/api/tickets/{ticket_id}", acting_user=acting_user, acting_role=acting_role)


async def update_ticket_status(
    ticket_id: int,
    target_status_code: str,
    remark: str | None = None,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """更新工单状态（PUT /api/tickets/{id}/status）。
    target_status_code 为状态编码：DRAFT/PENDING/PROCESSING/RESOLVED/CLOSED。"""
    body = {"targetStatusCode": target_status_code}
    if remark is not None:
        body["remark"] = remark
    return await request(
        "PUT", f"/api/tickets/{ticket_id}/status", json=body, acting_user=acting_user, acting_role=acting_role
    )


async def delete_ticket(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """删除工单（DELETE /api/tickets/{id}）"""
    return await request("DELETE", f"/api/tickets/{ticket_id}", acting_user=acting_user, acting_role=acting_role)


async def update_ticket(
    ticket_id: int,
    payload: dict,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """更新工单基本信息（PUT /api/tickets/{id}，部分更新）"""
    return await request(
        "PUT", f"/api/tickets/{ticket_id}", json=payload, acting_user=acting_user, acting_role=acting_role
    )


async def withdraw_ticket(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """撤回工单（POST /api/tickets/{id}/withdraw）。报障人本人、未处理前可撤。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/withdraw", acting_user=acting_user, acting_role=acting_role
    )


async def submit_ticket(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """提交工单（POST /api/tickets/{id}/submit）。草稿态由创建者本人/ADMIN 提交，DRAFT→PENDING。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/submit", acting_user=acting_user, acting_role=acting_role
    )


async def assign_ticket(
    ticket_id: int,
    assignee_id: int,
    *,
    priority_id: int | None = None,
    remark: str | None = None,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """派单（POST /api/tickets/{id}/assign）。主管/管理员调用，指定处理人，可选优先级终审。"""
    body: dict = {"assigneeId": assignee_id}
    if priority_id is not None:
        body["priorityId"] = priority_id
    if remark is not None:
        body["remark"] = remark
    return await request(
        "POST", f"/api/tickets/{ticket_id}/assign", json=body, acting_user=acting_user, acting_role=acting_role
    )


async def transfer_department(
    ticket_id: int,
    new_dept_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """转部门（POST /api/tickets/{id}/transfer-department）。主管/管理员调用。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/transfer-department",
        json={"newDeptId": new_dept_id}, acting_user=acting_user, acting_role=acting_role
    )


async def get_ticket_history(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """查询工单操作历史（GET /api/tickets/{id}/history），返回 List。"""
    return await request(
        "GET", f"/api/tickets/{ticket_id}/history", acting_user=acting_user, acting_role=acting_role
    )


async def add_ticket_comment(
    ticket_id: int,
    content: str,
    *,
    is_internal: bool = False,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """添加工单评论/备注（POST /api/tickets/{id}/comments）。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/comments",
        json={"content": content, "isInternal": is_internal}, acting_user=acting_user, acting_role=acting_role
    )


async def list_ticket_comments(
    ticket_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """查询工单评论列表（GET /api/tickets/{id}/comments），返回 List。"""
    return await request(
        "GET", f"/api/tickets/{ticket_id}/comments", acting_user=acting_user, acting_role=acting_role
    )

# ===== 用户 / 部门 =====

async def get_user_by_id(
    user_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """查询用户详情 GET /api/users/{id}。返回 UserVO：realName/phone/email/deptId 等"""
    return await request("GET", f"/api/users/{user_id}", acting_user=acting_user, acting_role=acting_role)

async def get_department_by_id(
    dept_id: int,
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> dict:
    """查询部门详情 GET /api/departments/{id}。返回 DepartmentVO：含 name/location（部门地址）"""
    return await request("GET", f"/api/departments/{dept_id}", acting_user=acting_user, acting_role=acting_role)

async def list_all_departments(
    *,
    acting_user: int | None = None,
    acting_role: str | None = None,
) -> list:
    """查询全部部门 GET /api/departments/tree，返回 DepartmentVO 列表"""
    return await request("GET", "/api/departments/tree", acting_user=acting_user, acting_role=acting_role)
