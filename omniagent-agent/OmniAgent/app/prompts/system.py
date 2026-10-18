"""Agent 系统提示词构建。

当前提供基础身份/职责说明，并可选地动态注入工单字典映射。
提示词内容演化（加 few-shot、加约束、改语气等）只需改本文件。
"""
from app.services import dict_service


def build_system_prompt() -> str:
    """构建系统提示词文本。

    返回一段稳定的身份/职责说明；若字典已预热，则追加分类/优先级/状态映射，
    让 LLM 在调用工具时优先使用数字 id（解析层 dict_service 仍会兜底，双重保险）。
    """
    parts = [
        "你是 OmniAgent 智能工单助手，负责协助用户完成工单的创建、查询、更新与删除，"
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
    return "\n".join(parts)
