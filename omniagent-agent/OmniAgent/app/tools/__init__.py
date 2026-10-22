"""LangChain 工具集合（包入口）。

所有 @tool 定义分散在子模块（如 ticket_tools），在此统一登记到 `tools` 列表，
供图（app.services.agent_service）的 bind_tools / ToolNode 装配使用。

新增工具的步骤：
1. 在对应子模块（如 ticket_tools.py）定义 @tool 函数；
2. 在此 import 该函数；
3. 把它加入下面的 `tools` 列表。
无需改动图代码即可让 LLM 发现新工具。
"""
from app.tools.ticket_tools import (
    query_tickets,
    get_ticket,
    update_ticket_status,
    update_ticket,
    delete_ticket,
    withdraw_ticket,
    assign_ticket,
    transfer_department,
    get_ticket_history,
    add_ticket_comment,
    list_ticket_comments,
    get_user,
    get_department,
    list_departments,
    create_ticket_draft
)

# 已注册工具列表（graph 装配时直接使用）
tools = [
    query_tickets,
    get_ticket,
    update_ticket_status,
    update_ticket,
    delete_ticket,
    withdraw_ticket,
    assign_ticket,
    transfer_department,
    get_ticket_history,
    add_ticket_comment,
    list_ticket_comments,
    get_user,
    get_department,
    list_departments,
    create_ticket_draft,
]

__all__ = ["tools"]
