import request from '@/utils/request'
import type { ResponseResult } from '@/api/ticket'

/**
 * 工单分类下拉选项（与后端 CategoryOptionVO 对齐）
 */
export interface CategoryOptionVO {
  value: number
  label: string
  parentId: number | null
  sortOrder: number | null
}

/**
 * 工单状态下拉选项（与后端 StatusOptionVO 对齐）
 */
export interface StatusOptionVO {
  value: number
  label: string
  isTerminal: boolean | null
  sortOrder: number | null
}

/**
 * 工单优先级下拉选项（与后端 PriorityOptionVO 对齐）
 */
export interface PriorityOptionVO {
  value: number
  label: string
  responseSlaMinutes: number | null
  resolveSlaMinutes: number | null
  sortOrder: number | null
}

/**
 * 工单全部字典分组（与后端 DictGroupVO 对齐）
 */
export interface DictGroupVO {
  categories: CategoryOptionVO[]
  statuses: StatusOptionVO[]
  priorities: PriorityOptionVO[]
}

/**
 * 查询全部字典（分类/状态/优先级一次返回）
 */
export const getAllDicts = async (): Promise<ResponseResult<DictGroupVO>> => {
  return await request.get('/dict/all')
}

/**
 * 查询工单分类
 */
export const getCategories = async (): Promise<ResponseResult<CategoryOptionVO[]>> => {
  return await request.get('/dict/categories')
}

/**
 * 查询工单状态
 */
export const getStatuses = async (): Promise<ResponseResult<StatusOptionVO[]>> => {
  return await request.get('/dict/statuses')
}

/**
 * 查询工单优先级
 */
export const getPriorities = async (): Promise<ResponseResult<PriorityOptionVO[]>> => {
  return await request.get('/dict/priorities')
}
