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


# 单例，全项目复用
settings = Settings()
