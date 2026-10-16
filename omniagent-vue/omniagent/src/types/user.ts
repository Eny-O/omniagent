/**
 * 用户详情 VO
 */
export interface UserVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  deptId: number | null
  status: number
  createdAt: string
  updatedAt: string
}

/**
 * 用户列表项 VO
 */
export interface UserPageVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  deptId: number | null
  status: number
  createdAt: string
}

/**
 * 创建/编辑用户表单对象
 */
export interface UserForm {
  id?: number
  username: string
  password: string
  realName: string
  email: string
  phone: string
  deptId: number | null
  status: number
}

/**
 * 创建用户请求 DTO
 */
export interface UserCreateDTO {
  username: string
  password: string
  realName: string
  email: string
  phone: string
  deptId: number | null
  status: number
}

/**
 * 更新用户请求 DTO
 * <p>
 * 所有字段均为可选，传空字符串表示清空对应字段。
 * 用户名和密码不可通过本接口修改。
 */
export interface UserUpdateDTO {
  realName?: string
  email?: string
  phone?: string
  deptId?: number | null
  status?: number
}

/**
 * 用户分页查询参数
 */
export interface UserQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number | null
  deptId?: number | null
}
