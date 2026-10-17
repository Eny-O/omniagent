import json
from typing import TypedDict, Literal
from langchain.chat_models import init_chat_model
from langgraph.constants import START
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langgraph.constants import END
from langchain_core.messages import HumanMessage
from typing import Optional
from app.schemas.query_ticket import QueryTicketSchema
from langchain.tools import tool
from langchain_core.utils.function_calling import convert_to_openai_tool
from app.core.config import settings
from app.repositories import backend_client

llm = init_chat_model(model="deepseek:deepseek-v4-flash",
                      api_key=settings.deepseek_api_key,
                      base_url=settings.deepseek_base_url,
                      temperature=0.2,
                      max_tokens=2048
                      )

class OverAllState(MessagesState):
    user_input: str
    response: str

class InputState(TypedDict):
    user_input: str

class OutputState(TypedDict):
    response: str




@tool(args_schema=QueryTicketSchema)
async def query_tickets(
    keyword: Optional[str] | None = None,
    status: Optional[int] = None,
    priority_id: Optional[int] = None,
    category_id: Optional[int] = None,
    page: int = 1,
    page_size: int = 10,
) -> str:
    """查询工单。用于根据关键词、状态、优先级、分类等条件检索工单列表。"""
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

convert_to_openai_tool(query_tickets)
tools = [query_tickets]
llm_with_tools = llm.bind_tools(tools)

def bootstrap_node(state: InputState) -> OverAllState:
    return {
        "messages": [HumanMessage(state["user_input"])],
        "response": "",
        "user_input":state["user_input"],
    }

async def execute_node(state: OverAllState) -> OverAllState:
    response = await llm_with_tools.ainvoke(state["messages"])
    return {"messages": [response]}

def router(state: OverAllState) -> Literal["tool_node","finalize_node"]:
    if state["messages"][-1].tool_calls:
        return "tool_node"
    return "finalize_node"

def finalize_node(state: OverAllState) -> OutputState:
    return {"response": state["messages"][-1].content}

graph = StateGraph(OverAllState)

graph.add_node("bootstrap_node",bootstrap_node)
graph.add_node("execute_node",execute_node)
graph.add_node("tool_node",ToolNode(tools))
graph.add_node("finalize_node",finalize_node)

graph.add_edge(START,"bootstrap_node")
graph.add_edge("bootstrap_node", "execute_node")
graph.add_conditional_edges("execute_node",router,path_map=["tool_node","finalize_node"])
graph.add_edge("tool_node","execute_node")
graph.add_edge("finalize_node",END)

agent = graph.compile()
