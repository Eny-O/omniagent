from typing import Optional, List
from pydantic import BaseModel, Field


class QueryTicketSchema(BaseModel):
    # 1. 精确匹配字段（通常用于已知部分信息的情况）
    ticket_id: Optional[str] = Field(default=None, description="工单唯一ID，若用户明确提及单号则优先使用此字段")
    device_name: Optional[str] = Field(default=None, description="故障设备名称，如：打印机、电脑")
    error_code: Optional[str] = Field(default=None, description="错误代码")

    # 2. 状态与分类过滤字段
    status: Optional[str] = Field(default=None, description="工单状态，如：待处理、处理中、已完成、已关闭")
    urgency: Optional[str] = Field(default=None, description="紧急程度过滤：低/中/高")

    # 3. 模糊查询字段（用于自然语言中的宽泛搜索）
    keyword: Optional[str] = Field(default=None, description="全局搜索关键词，用于模糊匹配故障现象描述或设备名称")

    # 4. 分页与排序参数（Agent 查询必备，防止一次性拉取过多数据导致上下文溢出）
    page: int = Field(default=1, description="当前页码，从1开始")
    page_size: int = Field(default=10, description="每页返回的工单数量，建议默认10条")