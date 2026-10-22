"""Agent 图编排层（ReAct）。"""
from typing import TypedDict, Literal

from langchain.chat_models import init_chat_model
from langgraph.checkpoint.memory import MemorySaver
from langgraph.constants import START, END
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langchain_core.messages import HumanMessage, SystemMessage

from app.core.config import settings
from app.services import dict_service
from app.tools import tools
from app.prompts import build_system_prompt

from langgraph.config import get_config
from app.tools.ticket_tools import _identity_by_thread

llm = init_chat_model(
    model="deepseek:deepseek-v4-flash",
    api_key=settings.deepseek_api_key,
    base_url=settings.deepseek_base_url,
    temperature=0.2,
    max_tokens=2048,
)

# 工具统一在 app.tools 包登记，此处直接装配
llm_with_tools = llm.bind_tools(tools)


class OverAllState(MessagesState):
    user_input: str
    response: str
    acting_user: int | None = None
    acting_role: str | None = None


class InputState(MessagesState):
    user_input: str
    acting_user: int | None = None
    acting_role: str | None = None


class OutputState(TypedDict):
    response: str


async def bootstrap_node(state: InputState) -> OverAllState:
    try:
        await dict_service.ensure_loaded()
    except Exception as e:
        print(f"[warn] bootstrap 预热字典失败（Java 未启动？）：{e}")
    existing = state.get("messages") or []
    new_messages = []
    # 同一 thread 的多轮对话：System 提示词只在首轮加一次
    if not any(isinstance(m, SystemMessage) for m in existing):
        new_messages.append(SystemMessage(build_system_prompt(state.get("acting_role", None))))
    if state.get("user_input"):
        new_messages.append(HumanMessage(state["user_input"]))
    acting_user = state.get("acting_user", None)
    acting_role = state.get("acting_role", None)
    _tid = (get_config() or {}).get("configurable", {}).get("thread_id")
    if _tid is not None:
        _identity_by_thread[_tid] = (acting_user, acting_role)

    return {
        "messages": new_messages,
        "response": "",
        "user_input": state["user_input"],
        "acting_user": state.get("acting_user", None),
        "acting_role": state.get("acting_role", None),
    }


async def execute_node(state: OverAllState) -> OverAllState:
    response = await llm_with_tools.ainvoke(state["messages"])
    return {"messages": [response]}


def router(state: OverAllState) -> Literal["tool_node", "finalize_node"]:
    if state["messages"][-1].tool_calls:
        return "tool_node"
    return "finalize_node"


def finalize_node(state: OverAllState) -> OutputState:
    return {"response": state["messages"][-1].content}


graph = StateGraph(OverAllState)

graph.add_node("bootstrap_node", bootstrap_node)
graph.add_node("execute_node", execute_node)
graph.add_node("tool_node", ToolNode(tools))
graph.add_node("finalize_node", finalize_node)

graph.add_edge(START, "bootstrap_node")
graph.add_edge("bootstrap_node", "execute_node")
graph.add_conditional_edges(
    "execute_node", router, path_map=["tool_node", "finalize_node"]
)
graph.add_edge("tool_node", "execute_node")
graph.add_edge("finalize_node", END)

agent = graph.compile(checkpointer=MemorySaver())
