from fastapi import FastAPI
from starlette.middleware.cors import CORSMiddleware
from app.api.agent import router as agent_router
from contextlib import asynccontextmanager
from fastapi import Request



#  启动事件 lifespan —— ≈ @PostConstruct / ApplicationRunner
# 应用启动时初始化一次的东西：LLM 客户端、读取配置、预热字典缓存：
@asynccontextmanager
async def lifespan(app):
    # 启动时：初始化 LLM、加载 dict 缓存……
    yield
    # 关闭时：释放资源

# 设置 FastAPI 的主入口应用
app = FastAPI(title="OmniAgent AI 后端", version="1.0.0",lifespan=lifespan)

# 添加 CORS 中间件，允许所有来源的请求
app.add_middleware(CORSMiddleware,
                   allow_origins=["http://localhost:5173"],
                   allow_credentials=True,
                   allow_methods=["*"],
                   allow_headers=["*"])
# 挂载路由，前缀为 /agent
app.include_router(agent_router,prefix="/agent")


@app.exception_handler(Exception)
async def handle_exception(req: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"code": "500", "message": str(exc)})


@app.get("/")
async def root():
    return {"status": "OK"}

