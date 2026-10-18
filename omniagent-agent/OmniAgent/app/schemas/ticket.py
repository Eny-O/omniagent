"""工单相关工具的入参 schema（对齐 Java Ticket 接口）

仅用于 LangChain 工具入参校验，不参与业务；字段类型与 docs 中 TicketCreateDTO /
路径参数保持一致。分类/优先级允许"中文标签或数字 id"，由 dict_service 归一化。
"""
from typing import Optional, Union

from pydantic import BaseModel, Field


class QueryTicketSchema(BaseModel):
    keyword: Optional[str] = Field(default=None, description="搜索关键词")
    status: Optional[Union[int, str]] = Field(default=None, description="工单状态，可传中文标签(如'待处理')或数字id")
    priority_id: Optional[Union[int, str]] = Field(default=None, description="工单优先级，可传中文标签(如'高')或数字id")
    category_id: Optional[Union[int, str]] = Field(default=None, description="工单分类，可传中文标签(如'硬件故障')或数字id")
    page: int = Field(default=1, description="当前页码")
    page_size: int = Field(default=10, description="每页数量")

class CreateTicketSchema(BaseModel):
    title: str = Field(..., description="工单标题，必填")
    description: Optional[str] = Field(default=None, description="工单描述")
    category: Optional[Union[int, str]] = Field(
        default=None, description="工单分类，可传中文标签(如'硬件故障')或数字 id"
    )
    priority: Optional[Union[int, str]] = Field(
        default=None, description="工单优先级，可传中文标签(如'高')或数字 id"
    )
    source: Optional[str] = Field(default="AGENT", description="工单来源，默认 AGENT（Agent 创建）")
    contact_phone: Optional[str] = Field(default=None, description="联系人电话")
    contact_email: Optional[str] = Field(default=None, description="联系人邮箱")
    location: Optional[str] = Field(default=None, description="位置")
    expected_resolve_at: Optional[str] = Field(
        default=None, description="预计解决时间，ISO8601 字符串，如 2026-10-20T10:00:00"
    )


class GetTicketSchema(BaseModel):
    ticket_id: int = Field(..., description="工单ID")


class UpdateTicketStatusSchema(BaseModel):
    ticket_id: int = Field(..., description="工单ID")
    target_status_code: str = Field(
        ..., description="目标状态编码：DRAFT/PENDING/PROCESSING/RESOLVED/CLOSED"
    )
    remark: Optional[str] = Field(default=None, description="操作备注")


class DeleteTicketSchema(BaseModel):
    ticket_id: int = Field(..., description="工单ID")


class UpdateTicketInfoSchema(BaseModel):
    ticket_id: int = Field(..., description="工单ID")
    title: Optional[str] = Field(default=None, description="工单标题")
    description: Optional[str] = Field(default=None, description="工单描述")
    category: Optional[Union[int, str]] = Field(
        default=None, description="工单分类，可传中文标签(如'硬件故障')或数字 id"
    )
    priority: Optional[Union[int, str]] = Field(
        default=None, description="工单优先级，可传中文标签(如'高')或数字 id"
    )
    current_assignee_id: Optional[int] = Field(default=None, description="当前处理人ID")
    contact_phone: Optional[str] = Field(default=None, description="联系人电话（传空字符串可清空）")
    contact_email: Optional[str] = Field(default=None, description="联系人邮箱（传空字符串可清空）")
    location: Optional[str] = Field(default=None, description="位置（传空字符串可清空）")
    expected_resolve_at: Optional[str] = Field(
        default=None, description="预计解决时间，ISO8601 字符串（传空字符串可清空）"
    )
