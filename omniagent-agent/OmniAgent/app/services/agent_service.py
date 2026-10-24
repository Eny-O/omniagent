"""Agent 图编排层（ReAct）。"""
import re
from typing import TypedDict, Literal

from langchain.chat_models import init_chat_model
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg_pool import AsyncConnectionPool
from langgraph.constants import START, END
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langchain_core.messages import HumanMessage, SystemMessage, AIMessageChunk

from app.core.config import settings
from app.core.database import init_pool
from app.services import dict_service
from app.tools import tools
from app.prompts import build_system_prompt

from langgraph.config import get_config,get_stream_writer
from app.tools.ticket_tools import _identity_by_thread
from app.kb.retrieval import search_kb


llm = init_chat_model(
    model="deepseek:deepseek-v4-flash",
    api_key=settings.deepseek_api_key,
    base_url=settings.deepseek_base_url,
    temperature=0.2,
    max_tokens=2048,
    streaming=True,
    timeout=60.0,
    max_retries=2,  # 瞬时失败（限流/抖动）自动重试，提升韧性
)

# 工具统一在 app.tools 包登记，此处直接装配
llm_with_tools = llm.bind_tools(tools)


class OverAllState(MessagesState):
    user_input: str
    response: str
    acting_user: int | None = None
    acting_role: str | None = None
    kb_hits: list = []
    deflect: bool = False


class InputState(MessagesState):
    user_input: str
    acting_user: int | None = None
    acting_role: str | None = None


class OutputState(TypedDict):
    response: str


async def bootstrap_node(state: InputState) -> OverAllState:
    """初始化状态，加载字典，添加 SystemMessage。"""
    try:
        await dict_service.ensure_loaded()
    except Exception as e:
        print(f"[warn] bootstrap 预热字典失败（Java 未启动？）：{e}")
    try:
        await init_pool()
    except Exception as e:
        import traceback
        print(f"[warn] KB 向量库连接池初始化失败：{e}")
        traceback.print_exc()  # 把完整堆栈打到控制台，方便定位

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
        "user_input": state.get("user_input", ""),
        "acting_user": state.get("acting_user", None),
        "acting_role": state.get("acting_role", None),
    }

async def kb_retrieval_node(state: OverAllState) -> OverAllState:
    """对用户问题向量化检索 KB，结果存入 state.kb_hits。"""
    writer = get_stream_writer()
    writer({"stage": "kb_retrieval", "message": "正在检索知识库…"})
    try:
        hits = await search_kb(state["user_input"])
    except Exception as e:
        # 检索失败不应阻断建单流程，仅告警
        print(f"[warn] KB 检索失败（不影响建单流程）：{e}")
        hits = []
    return {"kb_hits": hits}


async def kb_answer_node(state: OverAllState) -> OverAllState:
    """命中 KB：用检索片段答复用户，不进工单流程（偏转收口）。
    答案通过 stream_writer 逐句下发（stage=kb_text），前端打字机呈现。
    """
    writer = get_stream_writer()
    writer({"stage": "kb_deflect", "message": "已为你匹配知识库答案，正在整理…"})
    threshold = settings.kb_deflect_threshold
    # 只保留 score >= 阈值的片段（deflect_gate 已保证至少有一条达标）
    hits = [h for h in state["kb_hits"] if (h.get("score") or 0) >= threshold]

    lines = ["（根据知识库为你解答）\n"]
    for i, h in enumerate(hits, 1):
        score = h.get("score")
        score_str = f"{score:.3f}" if isinstance(score, (int, float)) else "?"
        lines.append(
            f"[{i}] 来源：《{h.get('title')}》（相关度 {score_str}）\n{h.get('content')}\n"
        )
    lines.append("若以上仍未能解决你的问题，可继续告诉我，我将为你创建工单。")
    answer = "\n".join(lines)
    # 逐句下发，让前端流式呈现（按句号/感叹号/问号/换行切分）
    for seg in re.split(r"(?<=[。！？\n])", answer):
        if seg:
            writer({"stage": "kb_text", "message": seg})
    return {"messages": [answer], "deflect": True}

def deflect_gate(state: OverAllState) -> Literal["kb_answer_node", "execute_node"]:
    """纯分数阈值判定（图结构控制，不依赖 LLM 自觉）。
    kb_hits 最高分 ≥ 阈值 → 偏转，直接答复（kb_answer_node）；
    否则 → 进入ReAct execute_node。
    """
    hits = state.get("kb_hits") or []
    if hits:
        best = max((h.get("score", 0) or 0) for h in hits)
        if best >= settings.kb_deflect_threshold:   # 0.75
            return "kb_answer_node"
    return "execute_node"


async def execute_node(state: OverAllState) -> OverAllState:
    """ReAct 主推理节点：调用模型生成回复，完整保留 tool_calls 供 router 判定。"""
    response = await llm_with_tools.ainvoke(state["messages"])
    return {"messages": [response]}


def router(state: OverAllState) -> Literal["tool_node", "finalize_node"]:
    if state["messages"][-1].tool_calls:
        return "tool_node"
    return "finalize_node"


def finalize_node(state: OverAllState) -> OutputState:
    return {"response": state["messages"][-1].content}


graph = StateGraph(state_schema=OverAllState,input_schema=InputState,output_schema=OutputState)

graph.add_node("bootstrap_node", bootstrap_node)
graph.add_node("kb_retrieval_node", kb_retrieval_node)
graph.add_node("kb_answer_node", kb_answer_node)
graph.add_node("execute_node", execute_node)
graph.add_node("tool_node", ToolNode(tools))
graph.add_node("finalize_node", finalize_node)

graph.add_edge(START, "bootstrap_node")
graph.add_edge("bootstrap_node", "kb_retrieval_node")
graph.add_conditional_edges(
    "kb_retrieval_node", deflect_gate,path_map=["kb_answer_node", "execute_node"],
)
graph.add_edge("kb_answer_node", "finalize_node")
graph.add_conditional_edges(
    "execute_node", router, path_map=["tool_node", "finalize_node"]
)
graph.add_edge("tool_node", "execute_node")
graph.add_edge("finalize_node", END)

# 编译后的图 + 自建 PG 连接池（启动时经 init_agent() 赋值；持久化 checkpointer）
agent = None
_pg_pool = None


async def init_agent():
    """启动时调用一次：自建连接池、建 checkpoint 表、编译图（持久化 checkpointer）。

    连接池由本模块长期持有（运行期保持打开）；setup() 幂等（表已存在跳过）。
    必须在任何 stream_run/stream_resume 之前执行（main.py lifespan 调用）。
    """

    async def _configure_conn(conn):
        # LangGraph setup() 用 CREATE INDEX CONCURRENTLY，要求连接不在事务块内；
        # 官方 from_conn_string 即用 autocommit=True 连接，这里保持一致。
        # async 连接的 autocommit 是只读属性，必须 await set_autocommit()。
        await conn.set_autocommit(True)

    global agent, _pg_pool
    if agent is not None:
        return
    _pg_pool = AsyncConnectionPool(
        conninfo=settings.pg_dsn, open=False, min_size=1, max_size=10,configure=_configure_conn,
    )
    await _pg_pool.open()
    saver = AsyncPostgresSaver(_pg_pool)
    await saver.setup()  # 首次建 checkpoints/checkpoint_blobs/checkpoint_writes 三表
    agent = graph.compile(checkpointer=saver)


import json
from langgraph.types import Command


async def stream_run(req_input: dict, config: dict):
    """图级 astream 实时捕获：messages=LLM 逐 token（前端打字机）；custom=节点中间信息"""
    if agent is None:
        await init_agent()
    async for mode, data in agent.astream(
        req_input, config, stream_mode=["messages", "custom"]
    ):
        if mode == "messages":
            chunk, _metadata = data
            # 只把 AI 的 token 分块当打字机文本；工具结果等其它消息不进气泡
            if isinstance(chunk, AIMessageChunk):
                text = chunk.content if isinstance(chunk.content, str) else "".join(
                    p.text for p in chunk.content if hasattr(p, "text"))
                if text:
                    yield (f"event: token\ndata: "
                           f"{json.dumps({'text': text}, ensure_ascii=False)}\n\n")
        elif mode == "custom":
            # data 即 get_stream_writer() 写出的 dict（含 stage/message）
            yield (f"event: info\ndata: "
                   f"{json.dumps(data, ensure_ascii=False)}\n\n")
    async for frame in _emit_final(config):
        yield frame


async def stream_resume(resume_val: str, config: dict):
    """与 stream_run 同构，仅输入从 dict 换成 Command(resume=...)"""
    if agent is None:
        await init_agent()
    async for mode, data in agent.astream(
        Command(resume=resume_val), config, stream_mode=["messages", "custom"]
    ):
        if mode == "messages":
            chunk, _metadata = data
            if isinstance(chunk, AIMessageChunk):
                text = chunk.content if isinstance(chunk.content, str) else "".join(
                    p.text for p in chunk.content if hasattr(p, "text"))
                if text:
                    yield (f"event: token\ndata: "
                           f"{json.dumps({'text': text}, ensure_ascii=False)}\n\n")
        elif mode == "custom":
            yield (f"event: info\ndata: "
                   f"{json.dumps(data, ensure_ascii=False)}\n\n")
    async for frame in _emit_final(config):
        yield frame


async def _emit_final(config: dict):
    """图执行结束（或被中断）后，根据 state 的 interrupts 决定下发 need_confirm 还是 done：
      - 线程处于中断暂停态 -> 下发 need_confirm（含确认/取消取值）；
      - 否则 -> 下发 done（携带最终 reply）。
    """
    state = await agent.aget_state(config)
    interrupts = []
    for t in (state.tasks or []):
        interrupts.extend(getattr(t, "interrupts", []) or [])
    if interrupts:
        p = interrupts[0].value if hasattr(interrupts[0], "value") else interrupts[0]
        payload = p if isinstance(p, dict) else {"message": str(p)}
        payload["confirm_value"] = "yes"
        payload["reject_value"] = "no"
        payload["thread_id"] = (config or {}).get("configurable", {}).get("thread_id")
        yield f"event: need_confirm\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
        return
    final_text = state.values.get("response", "") if state.values else ""
    yield f"event: done\ndata: {json.dumps({'reply': final_text}, ensure_ascii=False)}\n\n"

