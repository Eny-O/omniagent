"""知识库检索工具（自助优先 / 偏转 deflection）。"""
from __future__ import annotations
from langchain.tools import tool
from langgraph.prebuilt import ToolRuntime
from pydantic import BaseModel, Field

from app.kb.retrieval import search_kb


class SearchKBSchema(BaseModel):
    """search_knowledge_base 的参数。"""
    query: str = Field(..., description="用户的故障描述或问题，用于检索知识库")


@tool(args_schema=SearchKBSchema)
async def search_knowledge_base(query: str) -> str:
    """检索内部知识库（自助优先）。

    当用户想【获取信息 / 查资料】——包括描述故障现象、询问操作方法或排查步骤，
    以及查询公司制度、政策、办事流程、文档说明等（如'公司的规章制度有哪些'、
    '年假政策怎么算'），**且未明确要求建单**时，优先调用本工具检索知识库；
    若返回的片段已包含可完整解决用户问题的方案，可直接基于片段回复用户。

    但若用户**明确要求创建工单 / 报障 / 派单**等，应优先走建单流程，
    本工具的检索结果仅作参考，不要因命中知识库文章而拒绝或拖延建单。
    """
    try:
        results = await search_kb(query)
    except Exception as e:
        return f"知识库检索失败：{e}"

    if not results:
        return "知识库未检索到相关内容。"

    lines = ["【知识库命中】以下片段可能解答用户问题（按相关度从高到低）："]
    for i, r in enumerate(results, 1):
        score = r.get("score")
        score_str = f"{score:.3f}" if isinstance(score, (int, float)) else "?"
        lines.append(
            f"\n[{i}] 来源：《{r.get('title')}》（相关度 {score_str}）\n{r.get('content')}"
        )
    lines.append(
        "\n请判断：若上述片段已能完整解决用户问题，直接据此回复用户；"
        "否则告知用户知识库暂无方案，并继续走建单流程。"
    )
    return "\n".join(lines)
