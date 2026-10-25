from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware
from app.api.agent import router as agent_router
from app.services import dict_service
from contextlib import asynccontextmanager
from fastapi import Request
from fastapi.responses import JSONResponse
from app.core.database import init_pool, close_pool


#  启动事件 lifespan —— ≈ @PostConstruct / ApplicationRunner
# 应用启动时初始化一次的东西：LLM 客户端、读取配置、预热字典缓存：
@asynccontextmanager
async def lifespan(app):
    # 预热字典缓存：Agent 创建/查询工单时需要 标签<->id 互转
    try:
        await dict_service.load_dicts()
    except Exception as e:
        # Java 未启动时不阻塞启动，仅告警
        print(f"[warn] 字典预热失败（Java 未启动？）：{e}")
    # 初始化 KB 向量库连接池（启动时建一次）
    try:
        await init_pool()
    except Exception as e:
        print(f"[warn] KB 向量库连接池初始化失败（PG 未启动或配置错？）：{e}")
    # 初始化持久化 checkpointer 并编译图（首次 startup 建 checkpoint 三表）
    try:
        from app.services.agent_service import init_agent
        await init_agent()
    except Exception as e:
        print(f"[warn] Agent checkpointer 初始化失败（PG 未启动或配置错？）：{e}")
    yield
    # 关闭时：释放 PG 连接池（KB 池 + checkpointer 池）
    await close_pool()
    try:
        from app.services import agent_service
        if agent_service._pg_pool is not None:
            await agent_service._pg_pool.close()
    except Exception:
        pass

# 设置 FastAPI 的主入口应用
app = FastAPI(title="OmniAgent AI 后端", version="1.0.0",lifespan=lifespan)

# 添加 CORS 中间件，允许所有来源的请求
app.add_middleware(CORSMiddleware,
                   allow_origins=["http://localhost:5173"],
                   allow_credentials=True,
                   allow_methods=["*"],
                   allow_headers=["*"])
# 挂载路由，统一前缀 /api（最终接口：/api/agent/chat）
app.include_router(agent_router)

# 全局异常处理：返回 JSON 响应（500 内部错误）
@app.exception_handler(Exception)
async def handle_exception(req: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"code": "500", "message": str(exc)})


@app.get("/")
async def root():
    return {"status": "OK"}

@app.get("/healthz")
async def healthz():
    """健康检查：返回各依赖就绪状态（探活用）。

    依赖未就绪只体现在 body 的 checks 字段，整体仍返回 200，
    避免 k8s/网关因依赖抖动误杀容器（雪崩）。
    """
    from app.core.config import settings
    checks: dict = {}

    # PG checkpointer 池（会话持久化）
    try:
        from app.services import agent_service
        checks["pg_checkpointer"] = (
            "ready" if (agent_service._pg_pool is not None and agent_service._pg_pool.open)
            else "not_ready"
        )
    except Exception:
        checks["pg_checkpointer"] = "unknown"

    # KB 向量库池
    try:
        from app.core.database import get_pool
        get_pool()  # 未初始化会抛 RuntimeError
        checks["kb_pg"] = "ready"
    except Exception:
        checks["kb_pg"] = "not_ready"

    # 关键配置
    checks["deepseek_config"] = (
        "ready" if settings.deepseek_api_key and settings.deepseek_base_url else "missing"
    )

    return {"status": "OK", "checks": checks}

