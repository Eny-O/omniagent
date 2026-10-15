import type { TicketCreateDTO, TicketForm, TicketPageVO, TicketQuery, TicketVO } from '@/types/ticket'
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
  const res = await request.get('/tickets/page', { params: query })
  return res as ResponseResult<PageResult<TicketPageVO>>
}

/**
 * 根据 ID 查询工单详情
 */
export const getTicketById = async (id: number): Promise<ResponseResult<TicketVO>> => {
  const res = await request.get(`/tickets/${id}`)
  return res as ResponseResult<TicketVO>
}

/**
 * 创建工单
 */
export const createTicket = async (
  dto: TicketCreateDTO,
): Promise<ResponseResult<TicketVO>> => {
  const res = await request.post('/tickets', dto)
  return res as ResponseResult<TicketVO>
}

/**
 * 编辑工单
 * <p>
 * 当前后端只提供创建/详情/状态更新/删除，编辑复用创建接口参数结构，
 * 实际项目中可由后端扩展 PUT /api/tickets/{id}。
 */
export const updateTicket = async (
  id: number,
  form: TicketForm,
): Promise<ResponseResult<TicketVO>> => {
  // 如果后端已有编辑接口，改为：request.put(`/tickets/${id}`, form)
  const res = await request.post('/tickets', form)
  return res as ResponseResult<TicketVO>
}

/**
 * 更新工单状态
 */
export const updateTicketStatus = async (
  id: number,
  targetStatusCode: string,
  remark?: string,
): Promise<ResponseResult<void>> => {
  const res = await request.put(`/tickets/${id}/status`, {
    targetStatusCode,
    remark,
  })
  return res as ResponseResult<void>
}

/**
 * 删除工单
 */
export const deleteTicket = async (id: number): Promise<ResponseResult<void>> => {
  const res = await request.delete(`/tickets/${id}`)
  return res as ResponseResult<void>
}
