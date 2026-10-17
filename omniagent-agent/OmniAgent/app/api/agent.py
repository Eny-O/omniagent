"""Agent 接口层（Controller）

POST /api/agent/chat  { message, user_id? } -> { reply }
当前为非流式；后续可改为 SSE / WebSocket 实现打字机效果。
"""
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.agent_service import agent

router = APIRouter()


class ChatRequest(BaseModel):
    message: str
    user_id: int | None = None  # 真实操作人，透传给 Java（X-Acting-User）


class ChatResponse(BaseModel):
    reply: str


@router.post("/agent/chat")
async def chat(req: ChatRequest) -> ChatResponse:
    reply = await agent.ainvoke({"user_input": req.message})
    return ChatResponse(reply=reply["response"])
