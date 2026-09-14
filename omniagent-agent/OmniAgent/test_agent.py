from typing import TypedDict, Annotated, Any

from langchain_deepseek import ChatDeepSeek
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import StateGraph, END, START
from langgraph.graph.message import add_messages
from langgraph.graph.state import CompiledStateGraph
from langgraph.types import interrupt, Command
from langgraph.errors import GraphInterrupt
from schemas.create_ticket import CreateTicketSchema
from schemas.query_ticket import QueryTicketSchema
from pydantic import BaseModel
from typing import Type, Dict, Optional
import json
import re


llm = ChatDeepSeek(
    model="deepseek-v4-flash",   # 也可写 model_name=
    temperature=0.7,
    max_tokens=4096,
)

class TicketState(TypedDict):
    messages: Annotated[list, add_messages]
    user_input: str
    intent: str     #  识别的意图: 故障报修 / 查询 / 催办 / 知识问答
    entities: dict  # 提取的实体
    missing_fields: list  # 判断的缺失字段
    response: str  # 回复内容

def extract_json(text: str) -> dict:
    text = str(text).strip()
    # 去掉开头的 ```json 或 ```
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {}


INTENT_SCHEMA_REGISTRY: Dict[str, Type[BaseModel]] = {
    "故障报修": CreateTicketSchema,
    "查询工单": QueryTicketSchema,
}



def recognize_intent(state: TicketState) -> TicketState:
    user_input = state["user_input"].lower()
    state["messages"].append(HumanMessage(content=user_input))
    print(f'user_input: {user_input},LLM意图识别')
    prompt = f"""
    你是一个智能工单助手。请分析用户的输入："{user_input}"
    返回一个严格的 JSON 格式，包含以下字段：
    - "intent": 只能是 "故障报修" / "知识问答" / "查询工单"  之一
    - "entities": 根据不同的意图提取出的关键信息（如故障报修需要设备、错误码等，没有则为空字典）
    """
    resp = llm.invoke([HumanMessage(content=user_input),
                SystemMessage(content=prompt)
                ])
    state["messages"].append(AIMessage(content=resp.content))
    print(repr(resp.content))
    try:
        # 解析 LLM 返回的 JSON
        parsed = extract_json(str(resp.content))
        state["intent"] = parsed.get("intent", "unknown")
        state["entities"] = parsed.get("entities", {})
    except json.JSONDecodeError:
        # 如果 LLM 没按格式输出，做兜底处理
        state["intent"] = "知识问答"
        state["entities"] = {}
    return state

def route_intent(state: TicketState) -> str:
    intent = state["intent"]
    if intent == "故障报修":
        return "fault_report"
    elif intent == "查询工单":
        return "query_ticket"
    elif intent == "知识问答":
        return "knowledge_qa"
    else:
        return "unknown"


# 通用的语义映射函数（接收动态的 schema_cls）
def map_entities_to_schema(state: TicketState, schema_cls: Optional[Type[BaseModel]]) -> dict:
    entities = state["entities"]
    if schema_cls is None:
        return {}

    schema_info = {
        name: field.description
        for name, field in schema_cls.model_fields.items()
    }

    prompt = f"""
    你是一个数据映射专家。请将以下提取出的实体映射到指定的 Schema 字段上。

    【提取出的实体】: {json.dumps(entities, ensure_ascii=False)}
    【目标 Schema 字段及含义】: {json.dumps(schema_info, ensure_ascii=False)}

    请返回一个 JSON 对象，key 必须是目标 Schema 的字段名。
    如果某个实体无法映射到任何字段，请忽略它。只返回纯 JSON。
    """
    response = llm.invoke(prompt)
    state["messages"].append(AIMessage(content=response.content))
    return extract_json(str(response.content))


# 通用的缺失字段校验函数
def is_missing_fields(state: TicketState) -> TicketState:
    intent = state["intent"]
    schema_cls = INTENT_SCHEMA_REGISTRY.get(intent, None)
    if schema_cls is None:
        return state

    # 1. 先让 LLM 把自然语言实体对齐到 Schema 字段
    aligned_entities = map_entities_to_schema(state, schema_cls)

    # 2. 遍历目标 Schema 检查必填项
    fields_missing = []
    for field_name, field_info in schema_cls.model_fields.items():
        # 检查是否为必填字段，且对齐后的数据中是否包含该字段
        if field_info.is_required() and field_name not in aligned_entities:
            fields_missing.append(field_name)
    state["missing_fields"] = fields_missing
    return state

def ask_user(state: TicketState) -> TicketState:
    missing_fields = state["missing_fields"]
    print(f'询问用户补充缺失_fields: {missing_fields}')
    # 生成追问文案
    question = (
        f"为了准确处理您的「{state['intent']}」请求，"
        f"还需要您补充以下信息：{', '.join(missing_fields)}。"
    )

    # 中断，等待前端把用户回答传回来
    user_supplement = interrupt({
        "type": "missing_fields",
        "missing_fields": missing_fields,
        "question": question,
    })

    # 恢复后，把用户补充的内容合并到 entities
    for field_name, value in user_supplement.items():
        if field_name in missing_fields:
            state["entities"][field_name] = value
    # 清理，避免残留
    state["missing_fields"] = []
    return state


def fault_report(state: TicketState) -> TicketState:
    user_input = state["user_input"].lower()
    print(f'user_input: {user_input},LLM报修故障')
    state["response"] = "故障报修成功"
    return state

def query_ticket(state: TicketState) -> TicketState:
    user_input = state["user_input"].lower()
    print(f'user_input: {user_input},LLM查询工单')
    state["response"] = "查询工单成功"
    return state

def knowledge_qa(state: TicketState) -> TicketState:
    user_input = state["user_input"].lower()
    print(f'user_input: {user_input},LLM知识问答')
    state["response"] = "知识问答成功"
    return state
def unknown(state: TicketState) -> TicketState:
    state["response"] = "未知意图"
    return state

graph = StateGraph(TicketState)

graph.add_node("recognize_intent", recognize_intent)
graph.add_node("is_missing_fields", is_missing_fields)
graph.add_node("ask_user", ask_user)
graph.add_node("fault_report", fault_report)
graph.add_node("query_ticket", query_ticket)
graph.add_node("knowledge_qa", knowledge_qa)
graph.add_node("unknown", unknown)
graph.add_node("route_intent", lambda state: state)


graph.add_edge(START, "recognize_intent")
graph.add_edge("recognize_intent", "is_missing_fields")
# 有缺失字段，询问用户,没有缺失字段，直接路由意图
graph.add_conditional_edges("is_missing_fields",
                             lambda state: "ask_user" if state["missing_fields"] else "route_intent",
                             {"ask_user": "ask_user", "route_intent": "route_intent"})
graph.add_edge("ask_user", "is_missing_fields")
# 路由意图
graph.add_conditional_edges("route_intent",
                            route_intent,
                             {
                            "fault_report": "fault_report",
                            "query_ticket": "query_ticket",
                            "knowledge_qa": "knowledge_qa",
                            "unknown": "unknown"})
graph.add_edge("fault_report", END)
graph.add_edge("query_ticket", END)
graph.add_edge("knowledge_qa", END)
graph.add_edge("unknown", END)

agent = graph.compile(checkpointer=InMemorySaver())
