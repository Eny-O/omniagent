"""Agent 接口层（Controller）

POST /api/agent/chat/stream  { message, thread_id, resume? } -> SSE
身份来自前端 Authorization: Bearer（JWT，由 Java 签发、Agent 同密钥验签），
不再信任 X-Acting-* 头。验签得到 uid/role 供工具做权限判断，原样透传 Bearer 给 Java。
"""
from pydantic import BaseModel, Field
from fastapi.responses import StreamingResponse
from fastapi import APIRouter, Request
import json
import asyncio

from app.services.agent_service import stream_run, stream_resume
from app.utils.jwt import decode_token

router = APIRouter()


class ChatRequest(BaseModel):
    message: str = Field(default="", max_length=4000)
    thread_id: str = Field(min_length=1, max_length=128)
    resume: str | None = None


def _cfg(uid, role, token, thread_id: str) -> dict:
    # acting_user/acting_role 供工具做权限判断；bearer 原样透传给 Java
    return {"configurable": {"thread_id": thread_id,
                             "acting_user": uid, "acting_role": role, "bearer": token}}


def _error_stream(message: str) -> StreamingResponse:
    async def _gen():
        yield (f"event: error\ndata: "
               f"{json.dumps({'message': message}, ensure_ascii=False)}\n\n")
    return StreamingResponse(_gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


@router.post("/api/agent/chat/stream")
async def chat_stream(req: ChatRequest, request: Request):
    # 身份来自前端 Bearer；缺失或验签失败均直接拒绝（不下发业务流）
    auth = request.headers.get("Authorization", "")
    token = auth[len("Bearer "):].strip() if auth.startswith("Bearer ") else ""
    if not token:
        return _error_stream("缺少身份凭证（Authorization: Bearer）")
    try:
        payload = decode_token(token)
    except Exception as e:
        return _error_stream(f"身份校验失败：{e}")

    uid = payload.get("uid")
    role = payload.get("role")
    cfg = _cfg(uid, role, token, req.thread_id)

    # resume（确认/恢复）场景 message 允许为空；非 resume 的首次提问必须带内容
    if req.resume is None and not req.message.strip():
        return _error_stream("提问内容不能为空")

    async def event_gen():
        try:
            gen = (
                stream_resume(req.resume, cfg)
                if req.resume is not None
                else stream_run(
                    {"user_input": req.message,
                     "acting_user": uid, "acting_role": role},
                    cfg,
                )
            )
            async for frame in gen:
                if await request.is_disconnected():
                    break  # 客户端断开：退出循环会触发 gen.aclose()，取消底层 astream
                yield frame
        except asyncio.CancelledError:
            return  # 客户端断开导致任务被取消，静默退出
        except Exception as e:
            # 工具调 Java 失败 / LLM 异常等：转成 error 帧，避免流断裂或 500
            yield (
                f"event: error\ndata: "
                f"{json.dumps({'message': f'服务异常：{e}'}, ensure_ascii=False)}\n\n"
            )

    return StreamingResponse(
        event_gen(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
