"""Agent 接口层（Controller）

POST /api/agent/chat  { message, user_id?, acting_role? } -> { reply }
当前为非流式；后续可改为 SSE / WebSocket 实现打字机效果。

身份透传：把真实操作人的 user_id / acting_role 通过 LangGraph 的
config["configurable"] 传下去，工具内用 ToolRuntime 读取（见 app.tools.ticket_tools）。
"""
from fastapi import APIRouter
from langgraph.errors import GraphInterrupt
from pydantic import BaseModel
from langgraph.types import Command

from app.services.agent_service import agent

router = APIRouter()


class ChatRequest(BaseModel):
    message: str
    user_id: int | None = None  # 真实操作人，透传给 Java（X-Acting-User）
    acting_role: str | None = None  # 角色，透传给 Java（X-Acting-Role）
    thread_id: str  # 会话线程ID（interrupt/resume 必须，两次调用保持一致）
    resume: str | None = None  # 用户在草稿确认环节回复的内容


class ChatResponse(BaseModel):
    reply: str
    pending: bool = False
    pending_type: str | None = None
    draft: dict | None = None


def _cfg(req: ChatRequest) -> dict:
    return {"configurable": {"thread_id": req.thread_id,
                             "acting_user": req.user_id, "acting_role": req.acting_role}}


def _pending(agent, cfg) -> ChatResponse:
    state = agent.get_state(cfg)
    interrupts = []
    for t in (state.tasks or []):
        interrupts.extend(getattr(t, "interrupts", []) or [])
    if interrupts:
        p = interrupts[0].value if hasattr(interrupts[0], "value") else interrupts[0]
        return ChatResponse(
            reply=p.get("message", "请确认") if isinstance(p, dict) else str(p),
            pending=True,
            pending_type=p.get("type") if isinstance(p, dict) else None,
            draft=p.get("draft") if isinstance(p, dict) else None,
        )
    return ChatResponse(reply="（等待用户输入）")


@router.post("/api/agent/chat")
async def chat(req: ChatRequest) -> ChatResponse:
    cfg = _cfg(req)
    if req.resume is not None:                       # resume 模式：用户已回复确认/修改
        try:
            res = await agent.ainvoke(Command(resume=req.resume), config=cfg)
        except GraphInterrupt:
            return _pending(agent, cfg)
        return ChatResponse(reply=res["response"])

    try:
        res = await agent.ainvoke(
            {"user_input": req.message, "acting_user": req.user_id, "acting_role": req.acting_role},
            config=cfg,
        )
    except GraphInterrupt:
        return _pending(agent, cfg)
    return ChatResponse(reply=res["response"])
