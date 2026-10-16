<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DepartmentVO, DepartmentForm } from '@/types/department'
import {
  listAllDepartments,
  deleteDepartment,
  createDepartment,
  updateDepartment,
} from '@/api/department'
import DepartmentFormDialog from './DepartmentFormDialog.vue'

// 树形表格数据
const loading = ref(false)
const tableData = ref<DepartmentVO[]>([])
const allDepartments = ref<DepartmentVO[]>([])

// 弹窗
const dialogVisible = ref(false)
const currentDepartment = ref<DepartmentForm | undefined>(undefined)

// 将平铺数组转换为树形结构
const buildTree = (list: DepartmentVO[]): DepartmentVO[] => {
  const map = new Map<number, DepartmentVO>()
  const roots: DepartmentVO[] = []

  list.forEach((item) => {
    map.set(item.id, { ...item, children: [] })
  })

  list.forEach((item) => {
    const node = map.get(item.id)!
    if (item.parentId && map.has(item.parentId)) {
      const parent = map.get(item.parentId)!
      parent.children = parent.children || []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })

  // 清理空 children，保持表格展开箭头只在有子节点时显示
  const clean = (nodes: DepartmentVO[]): DepartmentVO[] => {
    return nodes.map((node) => {
      if (node.children && node.children.length > 0) {
        return { ...node, children: clean(node.children) }
      }
      const { children, ...rest } = node
      return rest
    })
  }

  return clean(roots)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listAllDepartments()
    if (res.code !== '200') {
      ElMessage.error(res.message || '查询失败')
      return
    }
    allDepartments.value = res.data || []
    tableData.value = buildTree(allDepartments.value)
  } finally {
    loading.value = false
  }
}

// 展开所有行
const expandAll = () => {
  // el-table 默认展开所有：给每一行设置到 expandedRows
  const ids: number[] = []
  const collect = (nodes: DepartmentVO[]) => {
    nodes.forEach((node) => {
      if (node.children && node.children.length > 0) {
        ids.push(node.id)
        collect(node.children)
      }
    })
  }
  collect(tableData.value)
  expandedRows.value = ids
}

const expandedRows = ref<number[]>([])

// 新建根部门
const handleCreateRoot = () => {
  currentDepartment.value = undefined
  dialogVisible.value = true
}

// 新增子部门
const handleCreateChild = (row: DepartmentVO) => {
  currentDepartment.value = {
    deptCode: '',
    deptName: '',
    parentId: row.id,
    sortOrder: 0,
  }
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: DepartmentVO) => {
  currentDepartment.value = {
    id: row.id,
    deptCode: row.deptCode,
    deptName: row.deptName,
    parentId: row.parentId,
    sortOrder: row.sortOrder,
  }
  dialogVisible.value = true
}

// 删除
const handleDelete = (row: DepartmentVO) => {
  ElMessageBox.confirm(`确定删除部门「${row.deptName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      const res = await deleteDepartment(row.id)
      if (res.code !== '200') {
        ElMessage.error(res.message || '删除失败')
        return
      }
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {
      // 取消删除
    })
}

// 提交表单
const handleSubmit = async (formData: DepartmentForm) => {
  if (formData.id) {
    // 编辑：转换为更新 DTO，排除 id、deptCode
    const { id, deptCode, ...updateDto } = formData
    const res = await updateDepartment(id, updateDto)
    if (res.code !== '200') {
      ElMessage.error(res.message || '修改失败')
      return
    }
    ElMessage.success('修改成功')
  } else {
    // 新建
    const res = await createDepartment(formData)
    if (res.code !== '200') {
      ElMessage.error(res.message || '创建失败')
      return
    }
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadData()
}

onMounted(() => {
  loadData().then(() => {
    expandAll()
  })
})
</script>

<template>
  <div class="department-list-container">
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>部门管理</span>
          <el-button type="primary" @click="handleCreateRoot">+ 新建部门</el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        default-expand-all
        :expand-row-keys="expandedRows"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        border
        style="width: 100%"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="220">
          <template #default="{ row }">
            <span class="dept-name">{{ row.deptName }}</span>
            <el-tag v-if="!row.parentId" size="small" type="info" class="root-tag">根部门</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptCode" label="部门编码" width="160" />
        <el-table-column prop="sortOrder" label="排序号" width="90" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCreateChild(row)">新增子部门</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <DepartmentFormDialog
      v-model:visible="dialogVisible"
      :department="currentDepartment"
      :all-departments="allDepartments"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.department-list-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dept-name {
  font-weight: 500;
}

.root-tag {
  margin-left: 8px;
}
</style>
