/**
 * 部门详情 VO（树形节点可递归）
 */
export interface DepartmentVO {
  id: number
  deptCode: string
  deptName: string
  parentId: number | null
  sortOrder: number
  createdAt: string
  updatedAt: string
  children?: DepartmentVO[]
}

/**
 * 创建/编辑部门表单对象
 */
export interface DepartmentForm {
  id?: number
  deptCode: string
  deptName: string
  parentId: number | null
  sortOrder: number
}

/**
 * 创建部门请求 DTO
 */
export interface DepartmentCreateDTO {
  deptCode: string
  deptName: string
  parentId: number | null
  sortOrder: number
}

/**
 * 更新部门请求 DTO
 * <p>
 * 部门编码不可修改。
 */
export interface DepartmentUpdateDTO {
  deptName?: string
  parentId?: number | null
  sortOrder?: number
}
