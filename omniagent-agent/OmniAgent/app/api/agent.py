"""Agent 接口层（Controller）

POST /api/agent/chat  { message, user_id?, acting_role? } -> { reply }
当前为非流式；后续可改为 SSE / WebSocket 实现打字机效果。

身份透传：把真实操作人的 user_id / acting_role 通过 LangGraph 的
config["configurable"] 传下去，工具内用 ToolRuntime 读取（见 app.tools.ticket_tools）。
"""
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.agent_service import agent

router = APIRouter()


class ChatRequest(BaseModel):
    message: str
    user_id: int | None = None  # 真实操作人，透传给 Java（X-Acting-User）
    acting_role: str | None = None  # 角色，透传给 Java（X-Acting-Role）


class ChatResponse(BaseModel):
    reply: str


@router.post("/api/agent/chat")
async def chat(req: ChatRequest) -> ChatResponse:
    reply = await agent.ainvoke(
        {"user_input": req.message, "acting_user": req.user_id, "acting_role": req.acting_role},
        config={"configurable": {"acting_user": req.user_id, "acting_role": req.acting_role}},
    )

    return ChatResponse(reply=reply["response"])
