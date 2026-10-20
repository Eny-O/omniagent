"""Agent 图编排层（ReAct）。"""
from typing import TypedDict, Literal

from langchain.chat_models import init_chat_model
from langgraph.constants import START, END
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langchain_core.messages import HumanMessage, SystemMessage

from app.core.config import settings
from app.tools import tools
from app.prompts import build_system_prompt

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


class InputState(TypedDict):
    user_input: str
    acting_user: int | None = None
    acting_role: str | None = None


class OutputState(TypedDict):
    response: str


def bootstrap_node(state: InputState) -> OverAllState:
    return {
        "messages": [
            SystemMessage(build_system_prompt()),
            HumanMessage(state["user_input"]),
        ],
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

agent = graph.compile()
