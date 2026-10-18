"""提示词（System Prompt）集中管理。

把系统提示词从图代码里抽出来，便于：
1. 随业务演进独立维护、做版本管理；
2. 动态注入字典映射（dict_service.to_prompt_text），辅助 LLM 更准确地产出规范标签。

新增/调整提示词时只改本包，无需改动图或工具代码。
"""
from app.prompts.system import build_system_prompt

__all__ = ["build_system_prompt"]
