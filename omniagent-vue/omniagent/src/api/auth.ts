import type { LoginDTO, LoginUserVO } from '@/types/auth'
import request from '@/utils/request'
import type { ResponseResult } from '@/api/ticket'

/**
 * 用户登录
 */
export const login = async (dto: LoginDTO): Promise<ResponseResult<LoginUserVO>> => {
  return await request.post('/auth/login', dto)
}

/**
 * 用户登出
 */
export const logout = async (): Promise<ResponseResult<void>> => {
  return await request.post('/auth/logout')
}

/**
 * 获取当前登录用户信息
 */
export const getCurrentUser = async (): Promise<ResponseResult<LoginUserVO>> => {
  return await request.get('/auth/me')
}
