"""工单相关 LangChain 工具。

每个工具封装一类后端能力（查/建/改/删），内部通过 backend_client 调 Java，
通过 dict_service 做「中文标签 ↔ id」归一化。新增工单工具时加在此文件，
并在 app/tools/__init__.py 的 tools 列表中登记。
"""
import json
from typing import Optional, Union

from langchain.tools import tool

from app.repositories import backend_client
from app.services import dict_service
from app.schemas.ticket import (
    QueryTicketSchema,
    CreateTicketSchema,
    GetTicketSchema,
    UpdateTicketStatusSchema,
    UpdateTicketInfoSchema,
    DeleteTicketSchema,
)


@tool(args_schema=QueryTicketSchema)
async def query_tickets(
    keyword: Optional[str] | None = None,
    status: Optional[Union[int, str]] = None,
    priority_id: Optional[Union[int, str]] = None,
    category_id: Optional[Union[int, str]] = None,
    page: int = 1,
    page_size: int = 10,
) -> str:
    """查询工单。用于根据关键词、状态、优先级、分类等条件检索工单列表。
    分类/优先级/状态可传中文标签或数字 id，由 dict_service 归一化为 Java 所需 id。"""
    # 归一化：中文标签或数字 id -> Java 需要的整数 id（从预热缓存取规则，无额外网络请求）
    category_id = await dict_service.resolve_category(category_id)
    priority_id = await dict_service.resolve_priority(priority_id)
    status = await dict_service.resolve_status(status)
    try:
        data = await backend_client.page_tickets(
            keyword=keyword,
            status_id=status,
            priority_id=priority_id,
            category_id=category_id,
            page=page,
            page_size=page_size,
        )
        records = data.get("records") or []
        if not records:
            return "未查询到相关工单。"
        return json.dumps(records, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"❌ 查询失败：{e}"


@tool(args_schema=CreateTicketSchema)
async def create_ticket(
    title: str,
    description: Optional[str] = None,
    category: Optional[Union[int, str]] = None,
    priority: Optional[Union[int, str]] = None,
    source: Optional[str] = "AGENT",
    contact_phone: Optional[str] = None,
    contact_email: Optional[str] = None,
    location: Optional[str] = None,
    expected_resolve_at: Optional[str] = None,
) -> str:
    """创建工单。当用户描述了一个故障/需求并希望新建工单时调用。
    分类/优先级可传中文标签（如'硬件故障'/'高'）或数字 id，由 dict_service 归一化。"""
    # 归一化：分类/优先级中文标签或数字 id -> Java 需要的整数 id；来源校验合法编码（复用预热缓存，无额外网络请求）
    category_id = await dict_service.resolve_category(category)
    priority_id = await dict_service.resolve_priority(priority)
    source_code = await dict_service.resolve_source(source) or "AGENT"
    payload: dict = {"title": title, "source": source_code}
    if description is not None:
        payload["description"] = description
    if category_id is not None:
        payload["categoryId"] = category_id
    if priority_id is not None:
        payload["priorityId"] = priority_id
    if contact_phone is not None:
        payload["contactPhone"] = contact_phone
    if contact_email is not None:
        payload["contactEmail"] = contact_email
    if location is not None:
        payload["location"] = location
    if expected_resolve_at is not None:
        payload["expectedResolveAt"] = expected_resolve_at
    try:
        data = await backend_client.create_ticket(payload)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"❌ 创建工单失败：{e}"


@tool(args_schema=GetTicketSchema)
async def get_ticket(ticket_id: int) -> str:
    """查询工单详情。当用户给出工单ID想看具体信息时调用。"""
    try:
        data = await backend_client.get_ticket(ticket_id)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"❌ 查询失败：{e}"


@tool(args_schema=UpdateTicketStatusSchema)
async def update_ticket_status(
    ticket_id: int, target_status_code: str, remark: Optional[str] = None
) -> str:
    """更新工单状态。当用户要求改变工单状态（如'标记为处理中'、'关闭工单'）时调用。
    target_status_code 用状态编码：DRAFT/PENDING/PROCESSING/RESOLVED/CLOSED。"""
    try:
        data = await backend_client.update_ticket_status(
            ticket_id, target_status_code, remark
        )
        return "✅ 工单状态已更新。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"❌ 更新失败：{e}"


@tool(args_schema=DeleteTicketSchema)
async def delete_ticket(ticket_id: int) -> str:
    """删除工单。当用户明确要求删除某个工单时调用。"""
    try:
        await backend_client.delete_ticket(ticket_id)
        return f"✅ 工单 {ticket_id} 已删除。"
    except backend_client.BackendError as e:
        return f"❌ 删除失败：{e}"


@tool(args_schema=UpdateTicketInfoSchema)
async def update_ticket(
    ticket_id: int,
    title: Optional[str] = None,
    description: Optional[str] = None,
    category: Optional[Union[int, str]] = None,
    priority: Optional[Union[int, str]] = None,
    current_assignee_id: Optional[int] = None,
    contact_phone: Optional[str] = None,
    contact_email: Optional[str] = None,
    location: Optional[str] = None,
    expected_resolve_at: Optional[str] = None,
) -> str:
    """更新工单基本信息。当用户要修改某工单的标题/描述/分类/优先级/处理人/联系方式/位置/预计解决时间时调用。
    分类/优先级可传中文标签(如'硬件故障'/'高')或数字 id，由 dict_service 归一化。
    只传需要修改的字段；文本字段传空字符串可清空。"""
    # 归一化：中文标签或数字 id -> Java 需要的整数 id（复用预热缓存，无额外网络请求）
    category_id = await dict_service.resolve_category(category)
    priority_id = await dict_service.resolve_priority(priority)
    payload: dict = {}
    if title is not None:
        payload["title"] = title
    if description is not None:
        payload["description"] = description
    if category_id is not None:
        payload["categoryId"] = category_id
    if priority_id is not None:
        payload["priorityId"] = priority_id
    if current_assignee_id is not None:
        payload["currentAssigneeId"] = current_assignee_id
    if contact_phone is not None:
        payload["contactPhone"] = contact_phone
    if contact_email is not None:
        payload["contactEmail"] = contact_email
    if location is not None:
        payload["location"] = location
    if expected_resolve_at is not None:
        payload["expectedResolveAt"] = expected_resolve_at
    try:
        data = await backend_client.update_ticket(ticket_id, payload)
        return "✅ 工单信息已更新。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"❌ 更新失败：{e}"
