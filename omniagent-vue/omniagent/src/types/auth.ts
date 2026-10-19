/**
 * 登录请求 DTO
 */
export interface LoginDTO {
  username: string
  password: string
}

/**
 * 登录表单对象
 */
export interface AuthForm {
  username: string
  password: string
}

/**
 * 登录成功返回的用户信息 VO
 */
export interface LoginUserVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  deptId: number | null
  role: string
  status: number
  createdAt: string
  loginAt: string
}
