"""工单相关 LangChain 工具。

每个工具封装一类后端能力（查/建/改/删），内部通过 backend_client 调 Java，
通过 dict_service 做「中文标签 ↔ id」归一化。新增工单工具时加在此文件，
并在 app/tools/__init__.py 的 tools 列表中登记。

身份透传：工具通过 LangGraph 注入的 `runtime: ToolRuntime` 读取当前请求的操作人身份
（user_id / role），其数据来自 api/agent.py 调用 ainvoke 时传入的
config["configurable"]。ToolRuntime 由框架自动注入、LLM 不可见，比手动透传 config
参数可靠（实测本版本中裸 config 参数不会注入到工具）。
"""
import json
from typing import Optional, Union

from langchain.tools import tool
from langgraph.prebuilt import ToolRuntime
from langgraph.types import interrupt

from app.repositories import backend_client
from app.services import dict_service
from app.schemas.ticket import (
    QueryTicketSchema,
    CreateTicketSchema,
    GetTicketSchema,
    UpdateTicketStatusSchema,
    UpdateTicketInfoSchema,
    DeleteTicketSchema,
    WithdrawTicketSchema,
    AssignTicketSchema,
    TransferDeptSchema,
    GetTicketHistorySchema,
    AddCommentSchema,
    ListCommentsSchema,
    GetUserSchema,
    GetDepartmentSchema,
    ListDepartmentsSchema,
)



REQUIRED_TICKET_FIELDS: dict[str, str] = {
    "title": "请用一句话概括故障标题（如：会议室投影仪无法开机）",
    "description": "请补充故障现象、发生时间与影响范围",
    "category": "请选择故障分类（如：硬件故障 / 软件问题 / 网络故障）",
    "priority": "请申报紧急程度（低 / 中 / 高）",

}

def _acting(runtime: ToolRuntime) -> tuple:
    """从 ToolRuntime 的 config 读取当前请求的操作人身份。"""
    cfg = (runtime.config or {}).get("configurable", {})
    return cfg.get("acting_user"), cfg.get("acting_role")


@tool(args_schema=QueryTicketSchema)
async def query_tickets(
    keyword: Optional[str] | None = None,
    status: Optional[Union[int, str]] = None,
    priority_id: Optional[Union[int, str]] = None,
    category_id: Optional[Union[int, str]] = None,
    page: int = 1,
    page_size: int = 10,
    runtime: ToolRuntime = None,
) -> str:
    """查询工单。用于根据关键词、状态、优先级、分类等条件检索工单列表。
    分类/优先级/状态可传中文标签或数字 id，由 dict_service 归一化为 Java 所需 id。"""
    acting_user, acting_role = _acting(runtime)
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
            acting_user=acting_user,
            acting_role=acting_role,
        )
        records = data.get("records") or []
        if not records:
            return "未查询到相关工单。"
        return json.dumps(records, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询工单列表失败：{e}"


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
    dept_id: Optional[Union[int, str]] = None,
    runtime: ToolRuntime = None,
) -> str:
    """创建工单。当用户描述了一个故障/需求并希望新建工单时调用。
    分类/优先级可传中文标签（如'硬件故障'/'高'）或数字 id，由 dict_service 归一化。"""
    acting_user, acting_role = _acting(runtime)
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
    if dept_id is not None:
        payload["deptId"] = int(dept_id) if str(dept_id).isdigit() else dept_id
    if contact_phone is not None:
        payload["contactPhone"] = contact_phone
    if contact_email is not None:
        payload["contactEmail"] = contact_email
    if location is not None:
        payload["location"] = location
    if expected_resolve_at is not None:
        payload["expectedResolveAt"] = expected_resolve_at
    try:
        data = await backend_client.create_ticket(payload, acting_user=acting_user, acting_role=acting_role)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"创建工单失败：{e}"


@tool(args_schema=GetTicketSchema)
async def get_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单详情。当用户给出工单ID想看具体信息时调用。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.get_ticket(ticket_id, acting_user=acting_user, acting_role=acting_role)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询工单详情失败：{e}"


@tool(args_schema=UpdateTicketStatusSchema)
async def update_ticket_status(
    ticket_id: int,
    target_status_code: str,
    remark: Optional[str] = None,
    runtime: ToolRuntime = None,
) -> str:
    """更新工单状态。当用户要求改变工单状态（如'标记为处理中'、'关闭工单'）时调用。
    target_status_code 用状态编码：DRAFT/PENDING/PROCESSING/RESOLVED/CLOSED。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.update_ticket_status(
            ticket_id, target_status_code, remark, acting_user=acting_user, acting_role=acting_role
        )
        return "工单状态已更新。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"更新工单状态失败：{e}"


@tool(args_schema=DeleteTicketSchema)
async def delete_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """删除工单。当用户明确要求删除某个工单时调用。该操作为硬删除，仅管理员(ADMIN)可执行。"""
    acting_user, acting_role = _acting(runtime)
    if acting_role != "ADMIN":
        return "❌ 删除工单为管理员专属操作，当前角色无权限。"
    try:
        await backend_client.delete_ticket(ticket_id, acting_user=acting_user, acting_role=acting_role)
        return f"工单 {ticket_id} 已删除。"
    except backend_client.BackendError as e:
        return f"删除工单失败：{e}"


@tool(args_schema=UpdateTicketInfoSchema)
async def update_ticket(
    ticket_id: int,
    title: Optional[str] = None,
    description: Optional[str] = None,
    category: Optional[Union[int, str]] = None,
    priority: Optional[Union[int, str]] = None,
    dept_id: Optional[Union[int, str]] = None,
    contact_phone: Optional[str] = None,
    contact_email: Optional[str] = None,
    location: Optional[str] = None,
    expected_resolve_at: Optional[str] = None,
    runtime: ToolRuntime = None,
) -> str:
    """更新工单基本信息。当用户要修改某工单的标题/描述/分类/优先级/受理部门/联系方式/位置/预计解决时间时调用。
    分类/优先级可传中文标签(如'硬件故障'/'高')或数字 id，由 dict_service 归一化。
    只传需要修改的字段；文本字段传空字符串可清空。"""
    acting_user, acting_role = _acting(runtime)
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
    if dept_id is not None:
        payload["deptId"] = int(dept_id) if str(dept_id).isdigit() else dept_id
    if contact_phone is not None:
        payload["contactPhone"] = contact_phone
    if contact_email is not None:
        payload["contactEmail"] = contact_email
    if location is not None:
        payload["location"] = location
    if expected_resolve_at is not None:
        payload["expectedResolveAt"] = expected_resolve_at
    try:
        data = await backend_client.update_ticket(ticket_id, payload, acting_user=acting_user, acting_role=acting_role)
        return "工单信息已更新。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"更新工单信息失败：{e}"


@tool(args_schema=WithdrawTicketSchema)
async def withdraw_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """撤回工单。当用户（报障人）希望撤销自己创建、尚未被处理的工单时调用。撤回后工单进入 CANCELLED 终态。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.withdraw_ticket(ticket_id, acting_user=acting_user, acting_role=acting_role)
        return "工单已撤回。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"撤回工单失败：{e}"


@tool(args_schema=AssignTicketSchema)
async def assign_ticket(
    ticket_id: int,
    assignee_id: int,
    priority_id: Optional[Union[int, str]] = None,
    remark: Optional[str] = None,
    runtime: ToolRuntime = None,
) -> str:
    """派单。当主管/管理员要将工单指派给某位处理人（HANDLER）时调用。
    可同时做优先级终审：priority_id 可传中文标签(如'高')或数字 id，不传则沿用报障人申报的优先级。"""
    acting_user, acting_role = _acting(runtime)
    priority_resolved = await dict_service.resolve_priority(priority_id) if priority_id is not None else None
    try:
        data = await backend_client.assign_ticket(
            ticket_id, assignee_id,
            priority_id=priority_resolved, remark=remark,
            acting_user=acting_user, acting_role=acting_role,
        )
        return "工单已派单。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"派单失败：{e}"


@tool(args_schema=TransferDeptSchema)
async def transfer_department(ticket_id: int, new_dept_id: int, runtime: ToolRuntime = None) -> str:
    """转部门。当主管/管理员需要将工单的受理部门变更到另一部门时调用（如该故障应由其他部门处理）。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.transfer_department(
            ticket_id, new_dept_id, acting_user=acting_user, acting_role=acting_role
        )
        return "工单已转部门。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"转部门失败：{e}"


@tool(args_schema=GetTicketHistorySchema)
async def get_ticket_history(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单操作历史。当用户想了解某工单的创建/修改/状态流转/派单/撤回等全程轨迹时调用。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.get_ticket_history(ticket_id, acting_user=acting_user, acting_role=acting_role)
        if not data:
            return "该工单暂无操作历史。"
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询操作历史失败：{e}"


@tool(args_schema=AddCommentSchema)
async def add_ticket_comment(
    ticket_id: int,
    content: str,
    is_internal: Optional[bool] = False,
    runtime: ToolRuntime = None,
) -> str:
    """添加工单评论/备注。当处理人需要记录处理进展、或与报障人沟通时调用。
    is_internal=True 表示内部备注（仅处理人/主管/管理员可见，报障人不可见）；默认 False 对报障人可见。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.add_ticket_comment(
            ticket_id, content, is_internal=bool(is_internal),
            acting_user=acting_user, acting_role=acting_role,
        )
        return "评论已添加。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"添加评论失败：{e}"


@tool(args_schema=ListCommentsSchema)
async def list_ticket_comments(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单评论列表。当用户想查看某工单的所有沟通记录/处理备注时调用。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.list_ticket_comments(ticket_id, acting_user=acting_user, acting_role=acting_role)
        if not data:
            return "该工单暂无评论。"
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询评论失败：{e}"

@tool(args_schema=GetUserSchema)
async def get_user(user_id: Optional[int] = None, runtime: ToolRuntime = None) -> str:
    """查询用户联系方式与归属部门。建单时用于自动补全联系电话/邮箱。
    不传 user_id 则查当前登录用户（操作人本人）。返回 JSON 含 realName/phone/email/deptId。"""
    acting_user, acting_role = _acting(runtime)
    uid = user_id if user_id is not None else acting_user
    if uid is None:
        return "无法确定要查询的用户：既未提供 user_id，当前也无登录用户上下文。"
    try:
        data = await backend_client.get_user_by_id(uid, acting_user=acting_user, acting_role=acting_role)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询用户失败：{e}"

@tool(args_schema=GetDepartmentSchema)
async def get_department(dept_id: int, runtime: ToolRuntime = None) -> str:
    """查询部门详情（含部门地址 location）。建单时把部门地址默认带为工单位置；也可确认受理部门。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.get_department_by_id(dept_id, acting_user=acting_user, acting_role=acting_role)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询部门失败：{e}"

@tool(args_schema=ListDepartmentsSchema)
async def list_departments(runtime: ToolRuntime = None) -> str:
    """查询所有部门（树形），建单时选择或确认受理部门。"""
    acting_user, acting_role = _acting(runtime)
    try:
        data = await backend_client.list_all_departments(acting_user=acting_user, acting_role=acting_role)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询部门列表失败：{e}"

@tool(args_schema=CreateTicketSchema)
async def create_ticket_draft(
    title: str,
    description: Optional[str] = None,
    category: Optional[Union[int, str]] = None,
    priority: Optional[Union[int, str]] = None,
    source: Optional[str] = "AGENT",
    contact_phone: Optional[str] = None,
    contact_email: Optional[str] = None,
    location: Optional[str] = None,
    expected_resolve_at: Optional[str] = None,
    dept_id: Optional[Union[int, str]] = None,
    runtime: ToolRuntime = None,
) -> str:
    """（推荐）自动拟单并提交草稿：先由系统推断字段，再 human-in-the-loop 暂停请用户确认/修改，
    确认后才真正创建工单。优先使用本工具，而非 create_ticket。"""
    acting_user, acting_role = _acting(runtime)
    category_id = await dict_service.resolve_category(category)
    priority_id = await dict_service.resolve_priority(priority)
    source_code = await dict_service.resolve_source(source) or "AGENT"

    draft: dict = {"title": title, "source": source_code}
    for k, v in {
        "description": description, "categoryId": category_id, "priorityId": priority_id,
        "deptId": (int(dept_id) if str(dept_id).isdigit() else dept_id) if dept_id is not None else None,
        "contactPhone": contact_phone, "contactEmail": contact_email,
        "location": location, "expectedResolveAt": expected_resolve_at,
    }.items():
        if v is not None:
            draft[k] = v

    # —— 人工确认打断 ——
    decision = interrupt({
        "type": "draft_review",
        "draft": draft,
        "message": "请确认以下工单草稿（回复「提交/确认」直接建单；需修改请直接说明要改的内容）：\n"
                   + json.dumps(draft, ensure_ascii=False, indent=2),
    })

    # —— 恢复：decision 为用户的确认文本或文字修改意见 ——
    if isinstance(decision, str) and decision.strip() in ("提交", "确认", "好的", "ok", "OK", "yes", "Yes", "确定"):
        try:
            data = await backend_client.create_ticket(draft, acting_user=acting_user, acting_role=acting_role)
            return "工单已创建（草稿态）：" + json.dumps(data, ensure_ascii=False)
        except backend_client.BackendError as e:
            return f"创建工单失败：{e}"

    return (f"用户希望对草稿做如下修改：{decision}。\n"
            "请根据该修改意见调整对应字段后，再次调用 create_ticket_draft 提交，不要追问已明确的信息。")

