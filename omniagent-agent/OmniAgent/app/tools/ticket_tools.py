"""工单相关 LangChain 工具。

每个工具封装一类后端能力（查/建/改/删），内部通过 backend_client 调 Java，
通过 dict_service 做「中文标签 ↔ id」归一化。新增工单工具时加在此文件，
并在 app/tools/__init__.py 的 tools 列表中登记。

身份透传：工具通过 LangGraph 注入的 `runtime: ToolRuntime` 读取当前请求的操作人身份
（user_id / role / bearer），其数据来自 api/agent.py 调用 ainvoke 时传入的
config["configurable"]。ToolRuntime 由框架自动注入、LLM 不可见，比手动透传 config
参数可靠（实测本版本中裸 config 参数不会注入到工具）。
bearer 为前端下发的 JWT，原样透传给 Java；user_id/role 供工具做权限判断。
"""
import json
from datetime import datetime, timedelta
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
    UpdateTicketInfoSchema,
    DeleteTicketSchema,
    WithdrawTicketSchema,
    SubmitTicketSchema,
    SlaAtRiskSchema,
    AssignTicketSchema,
    TransferDeptSchema,
    GetTicketHistorySchema,
    AddCommentSchema,
    ListCommentsSchema,
    GetUserSchema,
    GetDepartmentSchema,
    ListDepartmentsSchema,
    RecommendAssigneeSchema,
)



REQUIRED_TICKET_FIELDS: dict[str, str] = {
    "title": "请用一句话概括故障标题（如：会议室投影仪无法开机）",
    "description": "请补充故障现象、发生时间与影响范围",
    "category": "请选择故障分类（如：硬件故障 / 软件问题 / 网络故障）",
    "priority": "请申报紧急程度（低 / 中 / 高）",

}
_identity_by_thread: dict = {}
def _acting(runtime: ToolRuntime) -> tuple:
    """读取操作人身份（user_id, role, bearer），三级回退：
    1) FastAPI 通道：runtime.config.configurable
    2) Studio 首跑：runtime.state（dict）
    3) Studio/平台 resume（runtime=None）：get_config().configurable 取 thread_id → 查缓存
    """
    user_id = role = bearer = None
    if runtime is not None:
        cfg = (runtime.config or {}).get("configurable", {})
        user_id, role, bearer = cfg.get("acting_user"), cfg.get("acting_role"), cfg.get("bearer")
        if user_id is None or role is None or bearer is None:
            st = runtime.state
            vals = st if isinstance(st, dict) else getattr(st, "values", None) or {}
            user_id = user_id if user_id is not None else vals.get("acting_user")
            role = role if role is not None else vals.get("acting_role")
            bearer = bearer if bearer is not None else vals.get("bearer")
    if user_id is None or role is None or bearer is None:
        try:
            from langgraph.config import get_config
            cc = (get_config() or {}).get("configurable", {})
            user_id = user_id if user_id is not None else cc.get("acting_user")
            role = role if role is not None else cc.get("acting_role")
            bearer = bearer if bearer is not None else cc.get("bearer")
            if (user_id is None or role is None or bearer is None) and cc.get("thread_id"):
                cached = _identity_by_thread.get(cc["thread_id"])
                if cached:
                    user_id = user_id if user_id is not None else cached[0]
                    role = role if role is not None else cached[1]
                    bearer = bearer if bearer is not None else cached[2]
        except Exception:
            pass
    return user_id, role, bearer


# 其余草稿字段的中文键名（给人看的展示用）
_KEY_LABELS: dict[str, str] = {
    "title": "标题",
    "description": "故障描述",
    "contactPhone": "联系电话",
    "contactEmail": "联系邮箱",
    "location": "位置",
    "expectedResolveAt": "期望解决时间",
}


async def _dept_name(dept_id, bearer=None) -> str | None:
    """部门 id -> 部门名（仅用于给人看的草稿展示）。取不到时返回 None。"""
    try:
        data = await backend_client.get_department_by_id(
            int(dept_id), bearer=bearer
        )
        if isinstance(data, dict):
            # DepartmentVO 的部门名字段是 deptName（兼容 name）
            return data.get("deptName") or data.get("name")
        return None
    except Exception:
        return None


async def _user_name(user_id, bearer=None) -> str | None:
    """用户 id -> 姓名（realName / username，仅用于给人看的展示）。取不到时返回 None。"""
    try:
        data = await backend_client.get_user_by_id(
            int(user_id), bearer=bearer
        )
        if isinstance(data, dict):
            return data.get("realName") or data.get("username")
        return None
    except Exception:
        return None


async def _human_draft(draft: dict, bearer=None) -> dict:
    """把后台用的数字草稿转成全中文给人看的版本：
    分类/优先级/受理部门/来源转中文值，其余键名也统一转中文。
    返回新 dict，不修改入参。
    """
    display: dict = {}
    for k, v in draft.items():
        if v is None:
            continue
        if k == "categoryId":
            label = dict_service.category_label(v)
            display["分类"] = label if label is not None else v
        elif k == "priorityId":
            label = dict_service.priority_label(v)
            display["优先级"] = label if label is not None else v
        elif k == "deptId":
            name = await _dept_name(v, bearer=bearer)
            display["受理部门"] = name if name is not None else v
        elif k == "source":
            label = dict_service.source_label(v)
            display["来源"] = label if label is not None else v
        else:
            display[_KEY_LABELS.get(k, k)] = v
    return display


def _fmt_duration(minutes) -> str:
    """把分钟数转成中文时长：如 30 -> '30 分钟'，240 -> '4 小时'，150 -> '2 小时 30 分钟'。"""
    try:
        m = int(minutes)
    except (TypeError, ValueError):
        return str(minutes)
    if m < 60:
        return f"{m} 分钟"
    hours, rem = divmod(m, 60)
    if rem == 0:
        return f"{hours} 小时"
    return f"{hours} 小时 {rem} 分钟"


def _sla_intro(priority_id) -> str:
    """建单后提示：本优先级下需要的耗时（响应/解决 SLA）。查不到返回空串。"""
    resp = dict_service.priority_response_sla(priority_id)
    resol = dict_service.priority_resolve_sla(priority_id)
    if resp is None and resol is None:
        return ""
    parts = []
    if resp is not None:
        parts.append(f"{_fmt_duration(resp)}内响应")
    if resol is not None:
        parts.append(f"{_fmt_duration(resol)}内解决")
    return f"\n【SLA】本优先级下，预计需{'，'.join(parts)}（以实际受理为准）。"


def _sla_deadline_intro(priority_id) -> str:
    """提交后提示：从当前提交时间起算，预计在哪个时间点之前完成（基于解决 SLA）。查不到返回空串。"""
    resol = dict_service.priority_resolve_sla(priority_id)
    if resol is None:
        return ""
    deadline = datetime.now() + timedelta(minutes=resol)
    return (f"\n【SLA】已按本优先级解决时限（{_fmt_duration(resol)}）预估，"
            f"预计在 {deadline.strftime('%Y-%m-%d %H:%M')} 之前完成。")


def _interpret_decision(decision) -> str:
    """前端打断交互的三态判定：
    - 确认按钮 -> 'yes'  -> 'confirm'（执行建单/改单）
    - 取消按钮 -> 'no'   -> 'cancel'（直接结束，不做任何操作）
    - 其他操作输入框（自由文本）-> 'modify'（视为修改意见，交回 LLM 重新组织后再调用）
    """
    if not isinstance(decision, str):
        return "modify"
    d = decision.strip().lower()
    if d == "yes":
        return "confirm"
    if d == "no":
        return "cancel"
    return "modify"


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
    acting_user, acting_role, bearer = _acting(runtime)
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
            bearer=bearer,
        )
        records = data.get("records") or []
        if not records:
            return "未查询到相关工单。"
        return json.dumps(records, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询工单列表失败：{e}"


@tool(args_schema=GetTicketSchema)
async def get_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单详情。当用户给出工单ID想看具体信息时调用。"""
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.get_ticket(ticket_id, bearer=bearer)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询工单详情失败：{e}"


@tool(args_schema=DeleteTicketSchema)
async def delete_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """删除工单。当用户明确要求删除某个工单时调用。该操作为硬删除，不可恢复，仅管理员(ADMIN)可执行。"""
    acting_user, acting_role, bearer = _acting(runtime)
    if acting_role != "ADMIN":
        return "❌ 删除工单为管理员专属操作，当前角色无权限。"

    # 删除为不可逆高危操作，打断请用户确认（尽量带上工单标题便于核对）
    title = ""
    try:
        info = await backend_client.get_ticket(ticket_id, bearer=bearer)
        if isinstance(info, dict):
            title = info.get("title") or info.get("ticketNo") or ""
    except Exception:
        title = ""

    decision = interrupt({
        "type": "delete_review",
        "ticket_id": ticket_id,
        "message": (
            f"即将【硬删除】工单 #{ticket_id}"
            + (f"（{title}）" if title else "")
            + "，此操作不可恢复。\n"
            + "- 点击「确认」按钮执行删除；\n"
            + "- 点击「取消」按钮放弃本次删除；\n"
            + "- 或在下方输入框填写其他要执行的操作。\n"
        ),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            await backend_client.delete_ticket(ticket_id, bearer=bearer)
            return f"工单 {ticket_id} 已删除。"
        except backend_client.BackendError as e:
            return f"删除工单失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未被删除。"

    return (f"用户希望改执行其他操作：{decision}。\n"
            "请根据该诉求调用相应工具，不要执行删除。")


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
    acting_user, acting_role, bearer = _acting(runtime)
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

    # —— 人工确认打断（展示中文修改项，便于用户核对；只改用户指定的字段）——
    human = await _human_draft(payload, bearer=bearer)
    decision = interrupt({
        "type": "update_review",
        "ticket_id": ticket_id,
        "changes": payload,         # 后台实际使用的数字变更（技术视角）
        "human_changes": human,     # 给人看的中文变更
        "message": f"即将修改工单 #{ticket_id}，请确认以下变更项：\n"
                   f"- 点击「确认」按钮执行修改；\n"
                   f"- 点击「取消」按钮放弃本次修改；\n"
                   f"- 或在下方输入框填写要调整的内容。\n"
                   + json.dumps(human, ensure_ascii=False, indent=2),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.update_ticket(ticket_id, payload, bearer=bearer)
            return "工单信息已更新。" if not data else json.dumps(data, ensure_ascii=False)
        except backend_client.BackendError as e:
            return f"更新工单信息失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未做任何修改。"

    return (f"用户希望对本次修改做如下调整：{decision}。\n"
            "请根据该调整意见重新组织变更字段后，再次调用 update_ticket，不要追问已明确的信息。")


def _parse_dt(s) -> datetime | None:
    """把 ISO 时间串（如 '2026-10-22T10:00:00'）解析为 datetime，失败返回 None。"""
    if not s:
        return None
    try:
        return datetime.fromisoformat(str(s))
    except ValueError:
        return None


async def _fetch_all_tickets(acting_user, acting_role, bearer, category_id: Optional[int] = None) -> list:
    """分页拉取当前角色可见的全部工单（后端 DataScope 已按角色过滤）。page_size 上限 100。
    category_id 可选：仅拉该分类，缩小专长统计范围。"""
    all_records: list = []
    page, page_size, max_pages = 1, 100, 20
    while page <= max_pages:
        data = await backend_client.page_tickets(
            category_id=category_id,
            page=page, page_size=page_size, bearer=bearer
        )
        if not isinstance(data, dict):
            break
        records = data.get("records") or []
        all_records.extend(records)
        # 当前页返回条数不足一页（或为空）即说明已到末页，停止拉取（不依赖 total 字段）
        if len(records) < page_size:
            break
        page += 1
    return all_records


def _filter_sla_candidates(records, role, acting_user) -> list:
    """按角色筛选临期评估候选：主管=本部门待派单(TO_ASSIGN=7)无处理人；处理人=分配给我的待处理(2)/处理中(3)；其他=自己未闭环单。"""
    out: list = []
    for t in records or []:
        status_id = t.get("statusId")
        assignee = t.get("currentAssigneeId")
        if role == "SUPERVISOR":
            if status_id == 7 and not assignee:           # TO_ASSIGN 待派单(id=7) 且无处理人
                out.append(t)
        elif role == "HANDLER":
            if status_id in (2, 3) and assignee == acting_user:  # PENDING(2)/PROCESSING(3) 且处理人==我
                out.append(t)
        else:
            if status_id in (1, 7, 2, 3) and t.get("creatorId") == acting_user:  # DRAFT(1)/TO_ASSIGN(7)/PENDING(2)/PROCESSING(3) 本人未闭环
                out.append(t)
    return out


def _risk_header(role) -> str:
    if role == "SUPERVISOR":
        return "以下工单临近 SLA 解决时限且尚未派单，请优先派单："
    if role == "HANDLER":
        return "以下分配给你的工单临近 SLA 解决时限，请优先处理："
    return "以下你创建的工单临近 SLA 解决时限，可催促主管/处理人："


def _no_risk_message(role) -> str:
    return "✅ 当前没有临近 SLA 时限的工单。"


@tool(args_schema=SlaAtRiskSchema)
async def list_sla_at_risk(threshold_minutes: Optional[int] = None, runtime: ToolRuntime = None) -> str:
    """查询临近 SLA 解决时限的工单（按需）。当用户问"我有哪些快超时的工单/哪些工单快到期/帮我看看临期单"时调用。
    按当前角色自动圈定范围：主管=本部门待派单；处理人=分配给我的待处理/处理中；报障人=自己未闭环的工单。
    返回临期工单清单及建议动作（主管优先派单、处理人优先处理）。threshold_minutes 可自定义临期阈值（分钟），
    不传则默认规则：剩余时间 < 25% 解决SLA 或 < 2 小时（取较严）。"""
    acting_user, acting_role, bearer = _acting(runtime)
    records = await _fetch_all_tickets(acting_user, acting_role, bearer)
    if not records:
        return "当前没有可查看的工单。"
    candidates = _filter_sla_candidates(records, acting_role, acting_user)
    if not candidates:
        return _no_risk_message(acting_role)

    at_risk = []
    for t in candidates:
        priority_id = t.get("priorityId")
        created_at = _parse_dt(t.get("createdAt"))
        if created_at is None:
            continue
        sla = dict_service.priority_resolve_sla(priority_id)
        if sla is None:
            continue
        deadline = created_at + timedelta(minutes=sla)
        remaining = (deadline - datetime.now()).total_seconds() / 60
        threshold = threshold_minutes if threshold_minutes is not None else min(int(sla * 0.25), 120)
        if remaining < threshold:
            at_risk.append((t, remaining, priority_id))
    if not at_risk:
        return _no_risk_message(acting_role)

    at_risk.sort(key=lambda x: x[1])  # 剩余时间升序，最紧急的在前
    lines = [_risk_header(acting_role)]
    for t, remaining, priority_id in at_risk:
        label = dict_service.priority_label(priority_id) or priority_id
        status_lbl = dict_service.status_label(t.get("statusId")) or t.get("statusId")
        title = t.get("title") or ""
        tid = t.get("id")
        rem = max(0, int(remaining))
        lines.append(f"#{tid} {title}｜优先级 {label}｜状态 {status_lbl}｜剩余 {_fmt_duration(rem)}")
    return "\n".join(lines)


@tool(args_schema=SubmitTicketSchema)
async def submit_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """提交工单。当草稿态工单（由创建者本人创建）需要正式提交、进入待派单时调用。
    仅 DRAFT 草稿态 + 创建者本人/ADMIN 可提交（DRAFT→TO_ASSIGN）。"""
    acting_user, acting_role, bearer = _acting(runtime)

    # 提交会使工单由草稿进入待派单，打断请用户确认（best-effort 带标题便于核对）
    title = ""
    try:
        info = await backend_client.get_ticket(ticket_id, bearer=bearer)
        if isinstance(info, dict):
            title = info.get("title") or info.get("ticketNo") or ""
    except Exception:
        title = ""

    decision = interrupt({
        "type": "submit_review",
        "ticket_id": ticket_id,
        "message": (
            f"即将提交工单 #{ticket_id}" + (f"（{title}）" if title else "")
            + "，提交后将由草稿进入待派单（TO_ASSIGN），由主管派单后进入待处理（PENDING），再由处理人受理。\n"
            + "- 点击「确认」按钮执行提交；\n"
            + "- 点击「取消」按钮放弃本次提交；\n"
            + "- 或在下方输入框填写其他要执行的操作。\n"
        ),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.submit_ticket(ticket_id, bearer=bearer)
            if not data:
                return "工单已提交（待派单）。"
            return json.dumps(data, ensure_ascii=False) + _sla_deadline_intro(data.get("priorityId"))
        except backend_client.BackendError as e:
            return f"提交工单失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未提交。"

    return (f"用户希望改执行其他操作：{decision}。\n"
            "请根据该诉求调用相应工具，不要执行提交。")


@tool(args_schema=WithdrawTicketSchema)
async def withdraw_ticket(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """撤回工单。当用户（报障人）希望撤销自己创建、尚未被处理的工单时调用。撤回后工单进入 CANCELLED 终态。"""
    acting_user, acting_role, bearer = _acting(runtime)

    # 撤回会使工单进入 CANCELLED 终态，打断请用户确认（best-effort 带标题便于核对）
    title = ""
    try:
        info = await backend_client.get_ticket(ticket_id, bearer=bearer)
        if isinstance(info, dict):
            title = info.get("title") or info.get("ticketNo") or ""
    except Exception:
        title = ""

    decision = interrupt({
        "type": "withdraw_review",
        "ticket_id": ticket_id,
        "message": (
            f"即将撤回工单 #{ticket_id}" + (f"（{title}）" if title else "")
            + "，撤回后工单将进入 CANCELLED 终态，不可再处理。\n"
            + "- 点击「确认」按钮执行撤回；\n"
            + "- 点击「取消」按钮放弃本次撤回；\n"
            + "- 或在下方输入框填写其他要执行的操作。\n"
        ),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.withdraw_ticket(ticket_id, bearer=bearer)
            return "工单已撤回。" if not data else json.dumps(data, ensure_ascii=False)
        except backend_client.BackendError as e:
            return f"撤回工单失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未撤回。"

    return (f"用户希望改执行其他操作：{decision}。\n"
            "请根据该诉求调用相应工具，不要执行撤回。")


@tool(args_schema=RecommendAssigneeSchema)
async def recommend_assignee(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """派单前推荐最合适的处理人。当主管/管理员要派单但没想好派给谁时调用。
    基于：① 该工单所属部门的处理人(HANDLER)候选池；② 该工单分类的历史处理专长；
    ③ 各候选当前未闭环工单数(负载)。返回 Top-3 推荐 assigneeId 及姓名/专长分/负载，供派单确认时选择。"""
    acting_user, acting_role, bearer = _acting(runtime)
    if acting_role not in ("SUPERVISOR", "ADMIN"):
        return "推荐处理人仅主管/管理员可用，当前角色无派单权限。"

    # 取工单的部门与分类
    try:
        ticket = await backend_client.get_ticket(ticket_id, bearer=bearer)
    except backend_client.BackendError as e:
        return f"查询工单失败：{e}"
    if not isinstance(ticket, dict):
        return "未查询到该工单。"
    dept_id = ticket.get("deptId")
    category_id = ticket.get("categoryId")
    if dept_id is None:
        return "该工单未关联部门，无法按部门推荐处理人。"

    # 候选处理人：本部门 + 启用 + 角色=HANDLER
    handlers: list = []
    page, page_size = 1, 100
    while page <= 20:
        data = await backend_client.page_users(
            dept_id=dept_id, role="HANDLER", status=1,
            page=page, page_size=page_size, bearer=bearer,
        )
        if not isinstance(data, dict):
            break
        recs = data.get("records") or []
        handlers.extend(recs)
        # 当前页返回条数不足一页（或为空）即说明已到末页，停止拉取
        if len(recs) < page_size:
            break
        page += 1
    if not handlers:
        return "该部门下暂无启用的处理人(HANDLER)，无法推荐，请先确认部门或手动派单。"
    handler_ids = {u.get("id") for u in handlers}

    # 历史专长 + 负载：该分类工单中，各处理人的已解决/已关闭计数 与 未闭环计数
    hist = await _fetch_all_tickets(acting_user, acting_role, bearer, category_id=category_id)
    expertise = {hid: 0 for hid in handler_ids}
    load = {hid: 0 for hid in handler_ids}
    for t in hist or []:
        if acting_role == "ADMIN" and t.get("deptId") != dept_id:
            continue  # ADMIN 可见全量，需按目标部门过滤；SUPERVISOR DataScope 已限本部门
        aid = t.get("currentAssigneeId")
        if aid not in handler_ids:
            continue
        st = t.get("statusId")
        if st in (4, 5):            # RESOLVED(4) / CLOSED(5) → 专长
            expertise[aid] += 1
        if st in (2, 3):         # PENDING(2) / PROCESSING(3) → 负载（未闭环）
            load[aid] += 1

    # 排名：专长降序，同分负载升序，取 Top-3
    ranked = sorted(handler_ids, key=lambda hid: (-expertise.get(hid, 0), load.get(hid, 0)))[:3]

    # 组织结果（带姓名）
    lines = ["【派单推荐】基于部门 / 分类专长 / 负载，建议优先派给："]
    result = []
    for rank, hid in enumerate(ranked, 1):
        name = next((u.get("realName") or u.get("username") for u in handlers if u.get("id") == hid), hid)
        exp = expertise.get(hid, 0)
        ld = load.get(hid, 0)
        lines.append(f"{rank}. 处理人「{name}」(id={hid})｜本分类已解决 {exp} 单｜当前未闭环 {ld} 单")
        result.append({"assigneeId": hid, "realName": name, "expertise": exp, "openLoad": ld})
    lines.append("可在派单确认中选择上述任一人，或说“你定”由系统取第 1 名。")
    return "\n".join(lines) + "\n" + json.dumps(result, ensure_ascii=False)


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
    acting_user, acting_role, bearer = _acting(runtime)
    priority_resolved = await dict_service.resolve_priority(priority_id) if priority_id is not None else None

    # 派单为高影响操作，打断请用户确认（best-effort 带处理人姓名便于核对）
    assignee_name = await _user_name(assignee_id, bearer=bearer)
    priority_tip = ""
    if priority_resolved is not None:
        label = dict_service.priority_label(priority_resolved)
        priority_tip = f"，优先级终审为「{label or priority_resolved}」"
    decision = interrupt({
        "type": "assign_review",
        "ticket_id": ticket_id,
        "assignee_id": assignee_id,
        "priority_id": priority_resolved,
        "remark": remark,
        "message": (
            f"即将派单：工单 #{ticket_id} 指派给处理人「{assignee_name or assignee_id}」{priority_tip}。"
            + (f"\n备注：{remark}" if remark else "") + "\n"
            + "- 点击「确认」按钮执行派单；\n"
            + "- 点击「取消」按钮放弃本次派单；\n"
            + "- 或在下方输入框填写其他要执行的操作。\n"
        ),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.assign_ticket(
                ticket_id, assignee_id,
                priority_id=priority_resolved, remark=remark, bearer=bearer,
            )
            return "工单已派单。" if not data else json.dumps(data, ensure_ascii=False)
        except backend_client.BackendError as e:
            return f"派单失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未派单。"

    return (f"用户希望改执行其他操作：{decision}。\n"
            "请根据该诉求调用相应工具，不要执行派单。")


@tool(args_schema=TransferDeptSchema)
async def transfer_department(ticket_id: int, new_dept_id: int, runtime: ToolRuntime = None) -> str:
    """转部门。当主管/管理员需要将工单的受理部门变更到另一部门时调用（如该故障应由其他部门处理）。"""
    acting_user, acting_role, bearer = _acting(runtime)

    # 转部门为高影响操作，打断请用户确认（best-effort 带部门名便于核对）
    new_dept_name = await _dept_name(new_dept_id, bearer=bearer)
    decision = interrupt({
        "type": "transfer_review",
        "ticket_id": ticket_id,
        "new_dept_id": new_dept_id,
        "message": (
            f"即将把工单 #{ticket_id} 的受理部门转至「{new_dept_name or new_dept_id}」。\n"
            f"- 点击「确认」按钮执行转部门；\n"
            f"- 点击「取消」按钮放弃本次转部门；\n"
            f"- 或在下方输入框填写其他要执行的操作。\n"
        ),
    })

    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.transfer_department(
                ticket_id, new_dept_id, bearer=bearer
            )
            return "工单已转部门。" if not data else json.dumps(data, ensure_ascii=False)
        except backend_client.BackendError as e:
            return f"转部门失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未转部门。"

    return (f"用户希望改执行其他操作：{decision}。\n"
            "请根据该诉求调用相应工具，不要执行转部门。")


@tool(args_schema=GetTicketHistorySchema)
async def get_ticket_history(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单操作历史。当用户想了解某工单的创建/修改/状态流转/派单/撤回等全程轨迹时调用。"""
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.get_ticket_history(ticket_id, bearer=bearer)
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
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.add_ticket_comment(
            ticket_id, content, is_internal=bool(is_internal), bearer=bearer,
        )
        return "评论已添加。" if not data else json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"添加评论失败：{e}"


@tool(args_schema=ListCommentsSchema)
async def list_ticket_comments(ticket_id: int, runtime: ToolRuntime = None) -> str:
    """查询工单评论列表。当用户想查看某工单的所有沟通记录/处理备注时调用。"""
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.list_ticket_comments(ticket_id, bearer=bearer)
        if not data:
            return "该工单暂无评论。"
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询评论失败：{e}"


@tool(args_schema=GetUserSchema)
async def get_user(user_id: Optional[int] = None, runtime: ToolRuntime = None) -> str:
    """查询用户联系方式与归属部门。建单时用于自动补全联系电话/邮箱。
    不传 user_id 则查当前登录用户（操作人本人）。返回 JSON 含 realName/phone/email/deptId。"""
    acting_user, acting_role, bearer = _acting(runtime)
    uid = user_id if user_id is not None else acting_user
    if uid is None:
        return "无法确定要查询的用户：既未提供 user_id，当前也无登录用户上下文。"
    try:
        data = await backend_client.get_user_by_id(uid, bearer=bearer)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询用户失败：{e}"


@tool(args_schema=GetDepartmentSchema)
async def get_department(dept_id: int, runtime: ToolRuntime = None) -> str:
    """查询部门详情（含部门地址 location）。建单时把部门地址默认带为工单位置；也可确认受理部门。"""
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.get_department_by_id(dept_id, bearer=bearer)
        return json.dumps(data, ensure_ascii=False)
    except backend_client.BackendError as e:
        return f"查询部门失败：{e}"


@tool(args_schema=ListDepartmentsSchema)
async def list_departments(runtime: ToolRuntime = None) -> str:
    """查询所有部门（树形），建单时选择或确认受理部门。"""
    acting_user, acting_role, bearer = _acting(runtime)
    try:
        data = await backend_client.list_all_departments(bearer=bearer)
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
    """创建工单：先由系统推断字段，再 human-in-the-loop 暂停请用户确认/修改，
    确认后才真正创建工单。所有建单都应通过本工具，以保证人工确认环节。"""
    acting_user, acting_role, bearer = _acting(runtime)
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

    # —— 人工确认打断（展示中文草稿，便于用户核对）——
    human = await _human_draft(draft, bearer=bearer)
    decision = interrupt({
        "type": "draft_review",
        "draft": draft,            # 后台实际使用的数字草稿（技术视角）
        "human_draft": human,      # 给人看的中文草稿
        "message": "请确认以下工单草稿：\n"
                   "- 点击「确认」按钮直接建单；\n"
                   "- 点击「取消」按钮放弃本次创建；\n"
                   "- 或在下方输入框填写要修改的内容（将据此调整后再建单）。\n"
                   + json.dumps(human, ensure_ascii=False, indent=2),
    })

    # —— 恢复：按前端三态判定执行 ——
    action = _interpret_decision(decision)
    if action == "confirm":
        try:
            data = await backend_client.create_ticket(draft, bearer=bearer)
            return "工单已创建（草稿态）：" + json.dumps(data, ensure_ascii=False) + _sla_intro(data.get("priorityId"))
        except backend_client.BackendError as e:
            return f"创建工单失败：{e}"
    if action == "cancel":
        return "操作已取消，工单未创建。"

    return (f"用户希望对草稿做如下修改：{decision}。\n"
            "请根据该修改意见调整对应字段后，再次调用 create_ticket_draft 提交，不要追问已明确的信息。")
