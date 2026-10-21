import type {
  TicketAssignDTO,
  TicketComment,
  TicketCommentCreateDTO,
  TicketCreateDTO,
  TicketForm,
  TicketHistory,
  TicketPageVO,
  TicketQuery,
  TicketUpdateDTO,
  TicketVO,
} from '@/types/ticket'
import request from '@/utils/request'

// 统一响应结构（与后端 ResponseResult 对齐）
export interface ResponseResult<T> {
  code: string
  message: string
  data: T
}

// 分页结果结构（与后端 PageResult 对齐）
export interface PageResult<T> {
  total: number
  records: T[]
  pageNum: number
  pageSize: number
}

/**
 * 分页查询工单列表
 */
export const pageTickets = async (
  query: TicketQuery,
): Promise<ResponseResult<PageResult<TicketPageVO>>> => {
  return await request.get('/tickets/page', { params: query })
}

/**
 * 根据 ID 查询工单详情
 */
export const getTicketById = async (id: number): Promise<ResponseResult<TicketVO>> => {
  return await request.get(`/tickets/${id}`)
}

/**
 * 创建工单
 */
export const createTicket = async (
  dto: TicketCreateDTO,
): Promise<ResponseResult<TicketVO>> => {
  return await request.post('/tickets', dto)
}

/**
 * 编辑工单
 */
export const updateTicket = async (
  id: number,
  dto: TicketUpdateDTO,
): Promise<ResponseResult<TicketVO>> => {
  return await request.put(`/tickets/${id}`, dto)
}

/**
 * 更新工单状态
 */
export const updateTicketStatus = async (
  id: number,
  targetStatusCode: string,
  remark?: string,
): Promise<ResponseResult<void>> => {
  return await request.put(`/tickets/${id}/status`, {
    targetStatusCode,
    remark,
  })
}

/**
 * 删除工单
 */
export const deleteTicket = async (id: number): Promise<ResponseResult<void>> => {
  return await request.delete(`/tickets/${id}`)
}

/**
 * 撤回工单（报障人未处理前撤回 / 管理员撤回）
 */
export const withdrawTicket = async (id: number): Promise<ResponseResult<void>> => {
  return await request.post(`/tickets/${id}/withdraw`)
}

/**
 * 提交工单（创建者本人 / 管理员）：将草稿(DRAFT)转为待处理(PENDING)
 */
export const submitTicket = async (id: number): Promise<ResponseResult<TicketVO>> => {
  return await request.post(`/tickets/${id}/submit`)
}

/**
 * 派单（主管 / 管理员指定处理人并进入处理中）
 */
export const assignTicket = async (
  id: number,
  dto: TicketAssignDTO,
): Promise<ResponseResult<void>> => {
  return await request.post(`/tickets/${id}/assign`, dto)
}

/**
 * 转部门（主管 / 管理员：变更工单所属受理部门，并清除原部门指派避免越权滞留）
 * 后端校验：工单存在、非终态、SUPERVISOR 仅限本部门、ADMIN 不限、目标部门必须存在。
 */
export const transferDepartment = async (
  id: number,
  newDeptId: number,
): Promise<ResponseResult<TicketVO>> => {
  return await request.post(`/tickets/${id}/transfer-department`, { newDeptId })
}

/**
 * 查询工单操作历史（审计轨迹），按操作时间升序
 */
export const getTicketHistory = async (
  id: number,
): Promise<ResponseResult<TicketHistory[]>> => {
  return await request.get(`/tickets/${id}/history`)
}

/**
 * 查询工单评论列表（报障人仅返回非内部备注）
 */
export const listComments = async (
  id: number,
): Promise<ResponseResult<TicketComment[]>> => {
  return await request.get(`/tickets/${id}/comments`)
}

/**
 * 新增工单评论 / 备注（仅处理人 / 主管 / 管理员可写，终态工单禁止）
 */
export const addComment = async (
  id: number,
  dto: TicketCommentCreateDTO,
): Promise<ResponseResult<TicketComment>> => {
  return await request.post(`/tickets/${id}/comments`, dto)
}
