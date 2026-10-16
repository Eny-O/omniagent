import type {
  UserCreateDTO,
  UserForm,
  UserPageVO,
  UserQuery,
  UserUpdateDTO,
  UserVO,
} from '@/types/user'
import request from '@/utils/request'
import type { PageResult, ResponseResult } from '@/api/ticket'

/**
 * 分页查询用户列表
 */
export const pageUsers = async (
  query: UserQuery,
): Promise<ResponseResult<PageResult<UserPageVO>>> => {
  return await request.get('/users/page', { params: query })
}

/**
 * 根据 ID 查询用户详情
 */
export const getUserById = async (id: number): Promise<ResponseResult<UserVO>> => {
  return await request.get(`/users/${id}`)
}

/**
 * 创建用户
 */
export const createUser = async (dto: UserCreateDTO): Promise<ResponseResult<UserVO>> => {
  return await request.post('/users', dto)
}

/**
 * 更新用户
 */
export const updateUser = async (
  id: number,
  dto: UserUpdateDTO,
): Promise<ResponseResult<UserVO>> => {
  return await request.put(`/users/${id}`, dto)
}

/**
 * 删除用户
 */
export const deleteUser = async (id: number): Promise<ResponseResult<void>> => {
  return await request.delete(`/users/${id}`)
}
