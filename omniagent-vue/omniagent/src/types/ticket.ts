/**
 * 工单详情 VO
 */
export interface TicketVO {
  id: number
  ticketNo: string
  title: string
  description: string
  categoryId: number | null
  priorityId: number | null
  statusId: number
  creatorId: number
  currentAssigneeId: number | null
  source: string
  contactPhone: string
  contactEmail: string
  location: string
  expectedResolveAt: string | null
  resolvedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 工单列表项 VO
 */
export interface TicketPageVO {
  id: number
  ticketNo: string
  title: string
  categoryId: number | null
  priorityId: number | null
  statusId: number
  currentAssigneeId: number | null
  source: string
  createdAt: string
  updatedAt: string
}

/**
 * 创建/编辑工单表单对象
 */
export interface TicketForm {
  id?: number
  title: string
  description: string
  categoryId: number | null
  priorityId: number | null
  currentAssigneeId: number | null
  source: string
  contactPhone: string
  contactEmail: string
  location: string
  expectedResolveAt: string | null
}

/**
 * 创建工单请求 DTO
 */
export interface TicketCreateDTO {
  title: string
  description: string
  categoryId: number | null
  priorityId: number | null
  currentAssigneeId: number | null
  source: string
  contactPhone: string
  contactEmail: string
  location: string
  expectedResolveAt: string | null
}

/**
 * 更新工单请求 DTO
 * <p>
 * 所有字段均为可选，传空字符串表示清空对应字段。
 */
export interface TicketUpdateDTO {
  title?: string
  description?: string
  categoryId?: number | null
  priorityId?: number | null
  currentAssigneeId?: number | null
  contactPhone?: string
  contactEmail?: string
  location?: string
  expectedResolveAt?: string | null
}

/**
 * 更新工单状态请求 DTO
 */
export interface TicketUpdateStatusDTO {
  targetStatusCode: string
  remark: string
}

/**
 * 工单分页查询参数
 */
export interface TicketQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  statusId?: number | null
  priorityId?: number | null
  categoryId?: number | null
}
