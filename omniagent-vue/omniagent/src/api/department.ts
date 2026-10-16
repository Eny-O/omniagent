import type {
  DepartmentCreateDTO,
  DepartmentUpdateDTO,
  DepartmentVO,
} from '@/types/department'
import request from '@/utils/request'
import type { ResponseResult } from '@/api/ticket'

/**
 * 查询所有部门（树形结构用）
 */
export const listAllDepartments = async (): Promise<ResponseResult<DepartmentVO[]>> => {
  return await request.get('/departments/tree')
}

/**
 * 根据 ID 查询部门详情
 */
export const getDepartmentById = async (id: number): Promise<ResponseResult<DepartmentVO>> => {
  return await request.get(`/departments/${id}`)
}

/**
 * 创建部门
 */
export const createDepartment = async (
  dto: DepartmentCreateDTO,
): Promise<ResponseResult<DepartmentVO>> => {
  return await request.post('/departments', dto)
}

/**
 * 更新部门
 */
export const updateDepartment = async (
  id: number,
  dto: DepartmentUpdateDTO,
): Promise<ResponseResult<DepartmentVO>> => {
  return await request.put(`/departments/${id}`, dto)
}

/**
 * 删除部门
 */
export const deleteDepartment = async (id: number): Promise<ResponseResult<void>> => {
  return await request.delete(`/departments/${id}`)
}
