"""Java 后端 REST 客户端：Agent 调用 Java 的唯一网络边界

职责：
- 统一拼接 base_url（来自 Settings）
- 透传前端携带的 Authorization: Bearer（JWT，由 Java 签发、Agent 同密钥验签）
- 启动期系统等内部调用使用自签系统令牌（encode_system_token，role=ADMIN），避免冒充具体终端用户
- 解析 Java 统一响应 ResponseResult{code,message,data}，非 200 抛 BackendError
"""
from __future__ import annotations

import httpx

from app.core.config import settings
from app.utils.jwt import encode_system_token


class BackendError(Exception):
    """Java 后端返回的业务/HTTP 错误"""

    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message)
        self.status_code = status_code


def _build_headers(bearer: str | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    # 身份一律来自前端 Bearer（JWT）；不再使用 X-API-Key / X-Acting-* 头
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    return headers


async def request(
    method: str,
    path: str,
    *,
    params: dict | None = None,
    json: dict | None = None,
    bearer: str | None = None,
) -> dict:
    headers = _build_headers(bearer)
    async with httpx.AsyncClient(
        base_url=settings.omniagent_api_base_url, timeout=10.0
    ) as client:
        try:
            resp = await client.request(
                method, path, params=params, json=json, headers=headers
            )
        except httpx.TimeoutException:
            # except backend_client.BackendError 自动覆盖，返回友好提示而非技术异常
            raise BackendError("Java 后端响应超时（>10s），请稍后重试", 504)
        except httpx.RequestError as e:
            # 连接失败等传输层错误（ConnectError 等均为 RequestError 子类）
            raise BackendError(f"无法连接 Java 后端：{e}", 503)

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

    启动期预热调用，此时没有终端用户上下文，使用 Agent 自签系统令牌
    (encode_system_token，role=ADMIN) 通过 Java JWT 鉴权，而非冒充某位具体用户。
    """
    return await request("GET", "/api/dict/all", bearer=encode_system_token())


async def page_tickets(
    *,
    keyword: str = "",
    status_id: int | None = None,
    priority_id: int | None = None,
    category_id: int | None = None,
    page: int = 1,
    page_size: int = 10,
    bearer: str | None = None,
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
    return await request("GET", "/api/tickets/page", params=params, bearer=bearer)


async def create_ticket(payload: dict, *, bearer: str | None = None) -> dict:
    """创建工单（payload 对齐 Java TicketCreateDTO）"""
    return await request("POST", "/api/tickets", json=payload, bearer=bearer)


async def get_ticket(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """查询工单详情（GET /api/tickets/{id}）"""
    return await request("GET", f"/api/tickets/{ticket_id}", bearer=bearer)


async def update_ticket_status(
    ticket_id: int,
    target_status_code: str,
    remark: str | None = None,
    *,
    bearer: str | None = None,
) -> dict:
    """更新工单状态（PUT /api/tickets/{id}/status）。
    target_status_code 为状态编码：DRAFT/PENDING/PROCESSING/RESOLVED/CLOSED。"""
    body = {"targetStatusCode": target_status_code}
    if remark is not None:
        body["remark"] = remark
    return await request(
        "PUT", f"/api/tickets/{ticket_id}/status", json=body, bearer=bearer
    )


async def delete_ticket(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """删除工单（DELETE /api/tickets/{id}）"""
    return await request("DELETE", f"/api/tickets/{ticket_id}", bearer=bearer)


async def update_ticket(
    ticket_id: int,
    payload: dict,
    *,
    bearer: str | None = None,
) -> dict:
    """更新工单基本信息（PUT /api/tickets/{id}，部分更新）"""
    return await request(
        "PUT", f"/api/tickets/{ticket_id}", json=payload, bearer=bearer
    )


async def withdraw_ticket(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """撤回工单（POST /api/tickets/{id}/withdraw）。报障人本人、未处理前可撤。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/withdraw", bearer=bearer
    )


async def submit_ticket(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """提交工单（POST /api/tickets/{id}/submit）。草稿态由创建者本人/ADMIN 提交，DRAFT→PENDING。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/submit", bearer=bearer
    )


async def assign_ticket(
    ticket_id: int,
    assignee_id: int,
    *,
    priority_id: int | None = None,
    remark: str | None = None,
    bearer: str | None = None,
) -> dict:
    """派单（POST /api/tickets/{id}/assign）。主管/管理员调用，指定处理人，可选优先级终审。"""
    body: dict = {"assigneeId": assignee_id}
    if priority_id is not None:
        body["priorityId"] = priority_id
    if remark is not None:
        body["remark"] = remark
    return await request(
        "POST", f"/api/tickets/{ticket_id}/assign", json=body, bearer=bearer
    )


async def transfer_department(
    ticket_id: int,
    new_dept_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """转部门（POST /api/tickets/{id}/transfer-department）。主管/管理员调用。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/transfer-department",
        json={"newDeptId": new_dept_id}, bearer=bearer
    )


async def get_ticket_history(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """查询工单操作历史（GET /api/tickets/{id}/history），返回 List。"""
    return await request(
        "GET", f"/api/tickets/{ticket_id}/history", bearer=bearer
    )


async def add_ticket_comment(
    ticket_id: int,
    content: str,
    *,
    is_internal: bool = False,
    bearer: str | None = None,
) -> dict:
    """添加工单评论/备注（POST /api/tickets/{id}/comments）。"""
    return await request(
        "POST", f"/api/tickets/{ticket_id}/comments",
        json={"content": content, "isInternal": is_internal}, bearer=bearer
    )


async def list_ticket_comments(
    ticket_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """查询工单评论列表（GET /api/tickets/{id}/comments），返回 List。"""
    return await request(
        "GET", f"/api/tickets/{ticket_id}/comments", bearer=bearer
    )


# ===== 用户 / 部门 =====


async def get_user_by_id(
    user_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """查询用户详情 GET /api/users/{id}。返回 UserVO：realName/phone/email/deptId 等"""
    return await request("GET", f"/api/users/{user_id}", bearer=bearer)


async def get_department_by_id(
    dept_id: int,
    *,
    bearer: str | None = None,
) -> dict:
    """查询部门详情 GET /api/departments/{id}。返回 DepartmentVO：含 name/location（部门地址）"""
    return await request("GET", f"/api/departments/{dept_id}", bearer=bearer)


async def list_all_departments(
    *,
    bearer: str | None = None,
) -> list:
    """查询全部部门 GET /api/departments/tree，返回 DepartmentVO 列表"""
    return await request("GET", "/api/departments/tree", bearer=bearer)


async def page_users(
    *,
    dept_id: int | None = None,
    role: str | None = None,
    status: int | None = 1,
    keyword: str | None = None,
    page: int = 1,
    page_size: int = 100,
    bearer: str | None = None,
) -> dict:
    """分页查询用户（GET /api/users/page）。技能路由枚举某部门处理人用：
    可传 dept_id + role='HANDLER' + status=1。返回 PageResult{records,total}（无 pages 字段）。"""
    params: dict = {"pageNum": page, "pageSize": page_size}
    if dept_id is not None:
        params["deptId"] = dept_id
    if role is not None:
        params["role"] = role
    if status is not None:
        params["status"] = status
    if keyword:
        params["keyword"] = keyword
    return await request("GET", "/api/users/page", params=params, bearer=bearer)
