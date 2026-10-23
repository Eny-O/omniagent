"""LangChain 工具集合（包入口）。
所有 @tool 定义分散在子模块（如 ticket_tools），在此统一登记到 `tools` 列表，
供图（app.services.agent_service）的 bind_tools / ToolNode 装配使用。

"""
from app.tools.ticket_tools import (
    query_tickets,
    get_ticket,
    update_ticket,
    delete_ticket,
    withdraw_ticket,
    assign_ticket,
    transfer_department,
    get_ticket_history,
    add_ticket_comment,
    list_ticket_comments,
    submit_ticket,
    list_sla_at_risk,
    get_user,
    get_department,
    list_departments,
    create_ticket_draft,
    recommend_assignee
)
from app.tools.kb_tool import search_knowledge_base


# 已注册工具列表（graph 装配时直接使用）
tools = [
    query_tickets,
    get_ticket,
    update_ticket,
    delete_ticket,
    withdraw_ticket,
    assign_ticket,
    transfer_department,
    get_ticket_history,
    add_ticket_comment,
    list_ticket_comments,
    submit_ticket,
    list_sla_at_risk,
    get_user,
    get_department,
    list_departments,
    create_ticket_draft,
    recommend_assignee,
    search_knowledge_base,
]

__all__ = ["tools"]
