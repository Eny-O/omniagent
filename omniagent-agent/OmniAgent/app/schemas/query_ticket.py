from typing import Optional, List
from pydantic import BaseModel, Field


class QueryTicketSchema(BaseModel):
    keyword: Optional[str] = Field(default=None, description="搜索关键词")
    status: Optional[int] = Field(default=None, description="工单状态")
    priority_id: Optional[int] = Field(default=None, description="工单优先级ID")
    category_id: Optional[int] = Field(default=None, description="工单分类ID")
    page: int = Field(default=1, description="当前页码")
    page_size: int = Field(default=10, description="每页数量")