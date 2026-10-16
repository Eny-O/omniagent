import type {
  TicketCreateDTO,
  TicketForm,
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
