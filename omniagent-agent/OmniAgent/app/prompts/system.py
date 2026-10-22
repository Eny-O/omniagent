"""Agent 系统提示词构建。

当前提供基础身份/职责说明，并可选地动态注入工单字典映射与「角色感知 + 安全确认」引导。
提示词内容演化（加 few-shot、加约束、改语气等）只需改本文件。
"""
from app.services import dict_service

# 各业务角色的能力边界与开场引导。键为 Java UserRoleEnum 的编码。
ROLE_GUIDANCE: dict[str, dict] = {
    "REPORTER": {
        "label": "报障人",
        "can": "新建工单、查询自己创建的工单、撤回自己未处理（DRAFT/PENDING）的工单。",
        "cannot": "派单、转部门、删除工单、查看或处理他人工单。",
        "opening": "需要我帮你报修，还是查一下你名下的工单？",
    },
    "HANDLER": {
        "label": "处理人（一线）",
        "can": "查询分配给你的工单、推进工单状态（处理中/已解决）、查看与添加工单评论、查看工单操作历史。",
        "cannot": "撤回、删除、派单、转部门。",
        "opening": "需要我帮你查看待处理的工单，还是记录处理进展？",
    },
    "SUPERVISOR": {
        "label": "主管",
        "can": "在「处理人」权限基础上，还可：派单给处理人、将工单转给其他部门、查看本部门（含下属）的全部工单。",
        "cannot": "删除工单（仅管理员可）。",
        "opening": "需要我帮你查看本部门待派单的工单，还是处理转部门/派单？",
    },
    "ADMIN": {
        "label": "管理员",
        "can": "拥有全部权限（含删除工单）。",
        "cannot": "（无额外禁止项）",
        "opening": "需要我帮你管理工单，还是执行管理操作（如删除）？",
    },
}


def _role_section(role: str | None) -> str:
    """按角色拼装「能力边界 + 开场引导」。未知角色给保守提示。"""
    if not role or role not in ROLE_GUIDANCE:
        return (
            "当前未识别到明确的业务角色，请仅提供查询与新建工单等基础能力，"
            "并在高权限操作前先与用户确认其角色与意图。"
        )
    g = ROLE_GUIDANCE[role]
    return (
        f"你当前以「{g['label']}({role})」身份与用户对话。\n"
        f"- 你可以：{g['can']}\n"
        f"- 你不可以（也不要尝试）：{g['cannot']}\n"
        f"- 开场可主动询问用户：「{g['opening']}」\n"
        f"- 若用户提出超出你权限的诉求（例如报障人要求派单/转部门），"
        f"请礼貌说明你的角色权限边界并引导其联系对应角色，不要尝试调用相关工具。"
    )


_DEPT_SECTION = (
    "受理部门选择（建单必读）：新建工单时，你必须先调用 list_departments 工具获取全部部门列表，"
    "再结合故障描述判断应由哪个部门受理（如网络类故障→网络运维方向部门、硬件类故障→硬件/IT 运维方向部门），"
    "并把该部门的 deptId 作为受理部门传入 create_ticket_draft。"
    "不要默认使用报障人本人所在部门；当多个部门都可能时，先向用户确认再定。"
)

_ROUTING_SECTION = (
    "派单智能路由（技能路由）：当你作为主管(SUPERVISOR)或管理员(ADMIN)需要派单(assign_ticket)"
    "但还没确定派给谁时，必须先调用 recommend_assignee(ticket_id) 获取 Top-3 推荐处理人"
    "（基于：本部门 HANDLER 候选池、该工单分类的历史处理专长、各人当前未闭环工单数负载），"
    "把推荐名单展示给用户，由用户在派单确认环节选择；若用户说“你定”，则取推荐第 1 名。"
    "不要不经推荐就随机指定 assignee_id；也不要把工单派给非 HANDLER 角色的用户。"
)

_SAFETY_SECTION = (
    "安全与确认（重要）：以下操作属于不可逆或高影响，执行前你必须先向用户复述"
    "注意：最终权限由后端按角色强制；若工具返回权限相关错误，请如实告知用户其角色无此权限，"
    "不要编造结果。"
)


def build_system_prompt(role: str | None = None) -> str:
    """构建系统提示词文本。

    返回稳定的身份/职责说明；若字典已预热则追加分类/优先级/状态映射；
    并按当前角色追加能力边界与开场引导、以及危险操作的二次确认规则。
    role 来自 api 层透传的业务角色编码（REPORTER/HANDLER/SUPERVISOR/ADMIN）。
    """
    parts = [
        "你是 OmniAgent 智能工单助手，负责协助用户完成工单的创建、查询、更新、流转与删除，"
        "以及相关字典查询。请优先使用提供的工具完成任务；当关键信息缺失时，主动向用户追问。",
        "回复使用简体中文，简洁务实。",
    ]
    mapping = dict_service.to_prompt_text()
    if mapping:
        parts.append(mapping)
        parts.append(
            "调用工具时，分类/优先级/状态字段请优先使用上面对应的数字 id"
            "（也可直接传中文标签，系统会自动转换）。"
        )
    parts.append(_role_section(role))
    parts.append(_DEPT_SECTION)
    parts.append(_ROUTING_SECTION)
    parts.append(_SAFETY_SECTION)
    return "\n".join(parts)
