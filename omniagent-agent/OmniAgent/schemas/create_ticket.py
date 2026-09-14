from pydantic import BaseModel, Field
from typing import Optional


class CreateTicketSchema(BaseModel):
    device_name: str = Field(description="故障设备名称，如：打印机、电脑")
    error_desc: str = Field(description="故障现象描述")
    error_code: Optional[str] = Field(default=None, description="错误代码，如有")
    urgency: str = Field(default="中", description="紧急程度：低/中/高")