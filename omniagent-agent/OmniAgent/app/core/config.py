"""全局配置：从 .env 读取（已被 .gitignore 忽略，密钥不进版本库）"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # === DeepSeek 大模型 ===
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-v4-flash"

    # === Java 后端连接（Agent 调 Java REST 用）===
    omniagent_api_base_url: str = "http://localhost:8080"
    omniagent_api_key: str = "123456"

    # === PostgreSQL（KB 向量库，Agent 直连；与 Java 业务表同一 database）===
    pg_dsn: str = "postgresql://xx:xx@localhost:5432/xx"

    # === 知识库 embedding（阿里云百炼 qwen3.7-text-embedding）===
    dashscope_api_key: str = ""  # 百炼 API Key（和 deepseek 不同厂商，单独配）
    embedding_model: str = "qwen3.7-text-embedding"
    embedding_dim: int = 1024  # 锁死：必须和建表时 kb_chunks.embedding 的 vector(1024) 一致；改维度须重跑入库
    kb_chunk_size: int = 800  # 切块大小（字符），可按资料类型调
    kb_chunk_overlap: int = 120  # 切块重叠（字符），避免切到句子中间丢上下文
    kb_top_k: int = 5  # 检索返回前 k 条
    kb_rerank_enabled: bool = False  # rerank 预留开关，默认关
    kb_deflect_threshold: float = 0.75   # 偏转阈值：kb_hits 最高分≥此值才偏转；偏低易误偏转，偏高易漏答，后续按实测调



# 单例，全项目复用
settings = Settings()
