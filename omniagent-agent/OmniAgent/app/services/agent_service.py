"""Agent 图编排层（ReAct）。"""
import re
from typing import TypedDict, Literal

from langchain.chat_models import init_chat_model
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg_pool import AsyncConnectionPool
from langgraph.constants import START, END
from langgraph.graph import MessagesState, StateGraph
from langgraph.prebuilt import ToolNode
from langchain_core.messages import HumanMessage, SystemMessage, AIMessageChunk,AIMessage

from app.core.config import settings
from app.core.database import init_pool
from app.services import dict_service
from app.tools import tools
from app.prompts import build_system_prompt

from langgraph.config import get_config,get_stream_writer
from app.tools.ticket_tools import _identity_by_thread
from app.kb.retrieval import search_kb
from langgraph.types import RetryPolicy


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
    intent: str | None = None   # 意图分类（task / knowledge_qa / out_of_scope），由 intent_node 产出


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
    _cfg = (get_config() or {}).get("configurable", {})
    _tid = _cfg.get("thread_id")
    bearer = _cfg.get("bearer")
    if _tid is not None:
        # 缓存三元组 (user_id, role, bearer)，供工具在 resume 回退时读取
        _identity_by_thread[_tid] = (acting_user, acting_role, bearer)

    return {
        "messages": new_messages,
        "response": "",
        "user_input": state.get("user_input", ""),
        "acting_user": state.get("acting_user", None),
        "acting_role": state.get("acting_role", None),
    }


# 仅依赖基础 chat 能力（不依赖 function_calling / json_mode），兼容自建 deepseek 端点。
_INTENT_SYSTEM = (
    "你是工单助手的意图分类器。只判断用户这句话的意图类别，不要回答用户的问题。\n"
    "只有三类：\n"
    "1) knowledge_qa：用户想要【获取信息 / 查资料】，期望从公司知识库得到解答。"
    "典型包括：问操作方法、概念解释、配置说明、故障排查步骤，"
    "以及查询或列举公司制度、政策、办事流程、文档说明、名单目录等任何"
    "'想知道某类信息'的诉求。例如：'公司的规章制度有哪些'、'年假政策怎么算'、"
    "'VPN 怎么配置'、'系统登不上怎么办'、'公司网络ip是什么'。\n"
    "2) task：用户想要【执行动作】或闲聊。包括：创建/查询/派单/转部门/撤回/提交/"
    "删除工单等工单相关操作，以及问候、身份询问（'你是谁'）、闲聊。\n"
    "3) out_of_scope：与工单处理、公司制度/政策/操作方法完全无关的请求。"
    "典型包括：问天气、股票/基金/金融行情、新闻资讯、体育赛事、汇率等实时信息；"
    "与工单和公司知识库无关的通用闲聊、创作、娱乐、算命等；"
    "任何涉及企业外部系统或私人事务的诉求。例如：'今天天气怎么样'、"
    "'帮我看看股票行情'、'讲个笑话'、'帮我写首诗'。这类应礼貌拒绝，不要回答或调工具。\n"
    "判定要点：\n"
    "- 只要用户是来'问信息 / 查资料'而非'让我办件事' → knowledge_qa；\n"
    "- 用户明确要求建单/报障/派单等执行动作，或只是打招呼 → task；\n"
    "- 与工单/公司知识库完全无关（天气/股票/闲聊创作等） → out_of_scope；\n"
    "- 一句话既问方法又明确要求建单时，以执行诉求为准 → task；\n"
    "- 拿不准是否属于工单或公司知识库范畴时，宁判 task（交主流程按拒答规则二次判定），不要判 out_of_scope。\n"
    "只输出如下 JSON，不要任何多余文字或代码块标记：\n"
    '{"intent": "knowledge_qa 或 task 或 out_of_scope", "reason": "一句话依据"}'
)


async def intent_node(state: OverAllState) -> OverAllState:
    """轻量意图识别（LLM 推理，替代关键词穷举）。

    三分：task（任务/闲聊，含一切工具调用型）/ knowledge_qa（纯知识问答/查资料）/ out_of_scope（职责外）。
    默认 task（分类失败时兜底，交 execute_node：它仍可调 search_knowledge_base 工具查 KB，
    且系统提示词 _REFUSAL_SECTION 会对职责外诉求二次拒答，双保险）。
    """
    import json
    import re
    writer = get_stream_writer()
    text = (state.get("user_input") or "").strip()
    intent, reason = "task", "default"   # 默认 task，最安全（宁进主流程二次判定，不误杀）
    try:
        resp = await llm.ainvoke([
            SystemMessage(_INTENT_SYSTEM),
            HumanMessage(text),
        ])
        raw = resp.content if isinstance(resp.content, str) else "".join(
            p.text for p in resp.content if hasattr(p, "text"))
        # 兼容模型可能包裹 ```json 代码块或前后缀文字
        m = re.search(r"\{.*\}", raw, re.DOTALL)
        parsed = json.loads(m.group(0)) if m else {}
        intent = parsed.get("intent")
        reason = str(parsed.get("reason", ""))
        if intent not in ("task", "knowledge_qa", "out_of_scope"):
            intent, reason = "task", f"invalid:{intent}"
    except Exception as e:
        print(f"[warn] 意图 LLM 分类失败（兜底 task）：{type(e).__name__}: {e}")
        intent, reason = "task", "classify_error"
    writer({"stage": "intent", "intent": intent, "reason": reason})  # 可观测下发
    return {"intent": intent}


def intent_router(state: OverAllState) -> Literal["execute_node", "kb_retrieval_node", "refuse_node"]:
    """意图分发：
    task → execute_node（ReAct 调工具，含闲聊与职责外二次拒答兜底）；
    knowledge_qa → kb_retrieval_node（前置 KB 快捷通道）；
    out_of_scope → refuse_node（职责外，礼貌拒答后直接收口，不进 ReAct、不触工具）。"""
    if state.get("intent") == "knowledge_qa":
        return "kb_retrieval_node"
    if state.get("intent") == "out_of_scope":
        return "refuse_node"
    return "execute_node"


async def refuse_node(state: OverAllState) -> OverAllState:
    """职责外请求：固定礼貌拒答，不进 ReAct、不触发任何工具，直接收口。

    系统提示词(_REFUSAL_SECTION)已定义可问范围与拒绝话术；此处保证这类请求
    在意图层就被拦截，省一次主流程 LLM 调用，也避免误调工具。
    即便分类遗漏（兜底到 task）进了 execute_node，系统提示词仍会二次拒答（双保险）。
    """
    writer = get_stream_writer()
    writer({"stage": "refuse", "message": "检测到职责外请求，已礼貌拒答。"})
    answer = (
        "这超出了我的服务范围哦～我专注于工单处理与公司内部知识查询。\n"
        "你可以问我：如何创建工单、查询我的工单进度、某类故障怎么处理，"
        "或公司相关制度政策。"
    )
    for seg in re.split(r"(?<=[。！？\n])", answer):
        if seg:
            writer({"stage": "refuse_text", "message": seg})
    return {"messages": [AIMessage(content=answer)]}


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
    低分兜底：意图虽判为 knowledge_qa，但检索结果全低于阈值时，
    不硬给空/差答案，改为引导建单或联系人工。
    """
    writer = get_stream_writer()
    threshold = settings.kb_deflect_threshold
    hits = [h for h in (state.get("kb_hits") or []) if (h.get("score") or 0) >= threshold]

    # 低分兜底：意图判为知识问答，但检索结果都不达标 → 引导建单/人工，避免误给差答案
    if not hits:
        writer({"stage": "kb_deflect",
                "message": "未在知识库中找到足够相关的答案，建议创建工单或联系人工。"})
        answer = ("未在知识库中找到与此相关的答案。\n"
                  "你可以直接告诉我具体故障现象，我将为你创建工单；"
                  "或联系对应系统的人工支持。")
        for seg in re.split(r"(?<=[。！？\n])", answer):
            if seg:
                writer({"stage": "kb_text", "message": seg})
        return {"messages": [answer], "deflect": True}

    writer({"stage": "kb_deflect", "message": "已为你匹配知识库答案，正在整理…"})
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


async def execute_node(state: OverAllState) -> OverAllState:
    """ReAct 主推理节点：调用模型生成回复，完整保留 tool_calls 供 router 判定。"""
    try:
        response = await llm_with_tools.ainvoke(state["messages"])
    except Exception as e:
        print(f"[error] execute_node LLM 调用失败（已兜底）："
              f"{type(e).__name__}: {e}")
        return {
            "messages": [
                AIMessage(content="智能助手暂时不可用，请稍后重试；"
                                   "若问题紧急，可直接前往工单页创建工单。")
            ]
        }
    return {"messages": [response]}



def router(state: OverAllState) -> Literal["tool_node", "finalize_node"]:
    if state["messages"][-1].tool_calls:
        return "tool_node"
    return "finalize_node"


def finalize_node(state: OverAllState) -> OutputState:
    return {"response": state["messages"][-1].content}


graph = StateGraph(state_schema=OverAllState,input_schema=InputState,output_schema=OutputState)

graph.add_node("bootstrap_node", bootstrap_node)
graph.add_node("intent_node", intent_node)                        # 新增：意图识别
graph.add_node("kb_retrieval_node",
               kb_retrieval_node,
               retry_policy=RetryPolicy(max_attempts=2, initial_interval=1.0))
graph.add_node("kb_answer_node", kb_answer_node)
graph.add_node("refuse_node", refuse_node)                        # 新增：职责外拒答（不进 ReAct）
graph.add_node("execute_node", execute_node)
graph.add_node("tool_node", ToolNode(tools))
graph.add_node("finalize_node", finalize_node)

graph.add_edge(START, "bootstrap_node")
graph.add_edge("bootstrap_node", "intent_node")                    # 改：经意图节点分流
graph.add_conditional_edges(                                      # 新增：意图分发
    "intent_node", intent_router,
    path_map=["execute_node", "kb_retrieval_node", "refuse_node"]
)

graph.add_edge("kb_retrieval_node", "kb_answer_node")             # 改：意图前置决定，检索后直接给答案
graph.add_edge("kb_answer_node", "finalize_node")
graph.add_edge("refuse_node", "finalize_node")                    # 职责外拒答收口
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

