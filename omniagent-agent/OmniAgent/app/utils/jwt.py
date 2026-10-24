"""Agent 侧 JWT 工具：验签前端 Bearer 得到操作人身份；或自签系统调用令牌。

与 Java 端 JwtUtil 共用同一 HS256 密钥（settings.jwt_secret == Java jwt.secret），
payload 字段约定：uid(用户ID) / role(角色) / deptId(部门ID) / sub(用户名) / exp。
"""
from __future__ import annotations

import datetime as dt

import jwt as pyjwt

from app.core.config import settings

ALG = "HS256"


def decode_token(token: str) -> dict:
    """验签并返回 payload；失败抛 pyjwt 异常（ExpiredSignatureError / InvalidTokenError 等）。"""
    return pyjwt.decode(token, settings.jwt_secret, algorithms=[ALG])


def encode_system_token() -> str:
    """Agent 自身系统身份令牌（role=ADMIN，无终端用户），用于启动期预热等内部调用。

    启动期字典预热等场景没有终端用户上下文，自签一个系统令牌发给 Java，
    由 Java 的 JWT 过滤器验签得到 role=ADMIN 身份；不冒充任何具体真人。
    """
    now = dt.datetime.utcnow()
    payload = {
        "sub": "system",
        "uid": None,
        "role": "ADMIN",
        "deptId": None,
        "iat": now,
        "exp": now + dt.timedelta(minutes=settings.jwt_expiration_minutes),
    }
    return pyjwt.encode(payload, settings.jwt_secret, algorithm=ALG)
