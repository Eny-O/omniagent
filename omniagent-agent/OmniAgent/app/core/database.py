"""Agent 直连 PostgreSQL（KB 向量库）。

与 Java 业务库 同一 database，但只有 Agent 读写 kb_* 两张表，
本文件只提供连接池，不直接写业务 SQL。
"""
from __future__ import annotations

import asyncpg

from app.core.config import settings

# 全局连接池：应用启动时建一次，全请求复用
_pool: asyncpg.Pool | None = None


async def init_pool() -> None:
    """应用启动时调用一次：建立连接池。幂等（重复调用不会建多个）。"""
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(
            dsn=settings.pg_dsn,
            min_size=1,
            max_size=10,
            command_timeout=30,
        )


async def close_pool() -> None:
    """应用关闭时调用：释放连接池。"""
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None


def get_pool() -> asyncpg.Pool:
    """取已初始化的连接池。未初始化会抛错——用于及早发现你忘了 init_pool()。"""
    if _pool is None:
        raise RuntimeError("PG 连接池尚未初始化，请先调用 init_pool()")
    return _pool
