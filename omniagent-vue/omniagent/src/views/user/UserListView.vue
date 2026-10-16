<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UserPageVO, UserForm, UserUpdateDTO } from '@/types/user'
import { pageUsers, deleteUser, createUser, updateUser } from '@/api/user'
import UserFormDialog from './UserFormDialog.vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: null as number | null,
  deptId: null as number | null,
})

// 表格数据
const loading = ref(false)
const tableData = ref<UserPageVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 弹窗
const dialogVisible = ref(false)
const currentUser = ref<UserForm | undefined>(undefined)

// 用户状态选项
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

// 部门选项（TODO：后续接入部门接口动态加载）
const deptOptions = [
  { label: '技术部', value: 1 },
  { label: '运维部', value: 2 },
  { label: '客服部', value: 3 },
]

const getStatusTagType = (status: number): 'success' | 'danger' | 'info' => {
  const map: Record<number, 'success' | 'danger' | 'info'> = {
    1: 'success',
    0: 'danger',
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: number) => {
  return statusOptions.find((item) => item.value === status)?.label || '未知'
}

const getDeptLabel = (deptId: number | null) => {
  if (!deptId) return '-'
  return deptOptions.find((item) => item.value === deptId)?.label || '未知'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await pageUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      status: searchForm.status,
      deptId: searchForm.deptId,
    })
    if (res.code !== '200') {
      ElMessage.error(res.message || '查询失败')
      return
    }
    total.value = res.data.total
    tableData.value = res.data.records
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pageNum.value = 1
  loadData()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = null
  searchForm.deptId = null
  pageNum.value = 1
  loadData()
}

// 分页
const handleSizeChange = (val: number) => {
  pageSize.value = val
  loadData()
}

const handleCurrentChange = (val: number) => {
  pageNum.value = val
  loadData()
}

// 新建
const handleCreate = () => {
  currentUser.value = undefined
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: UserPageVO) => {
  currentUser.value = {
    id: row.id,
    username: row.username,
    password: '',
    realName: row.realName,
    email: row.email,
    phone: row.phone,
    deptId: row.deptId,
    status: row.status,
  }
  dialogVisible.value = true
}

// 删除
const handleDelete = (row: UserPageVO) => {
  ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      const res = await deleteUser(row.id)
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
const handleSubmit = async (formData: UserForm) => {
  if (formData.id) {
    // 编辑：转换为更新 DTO，排除 id、username、password
    const { id, username, password, ...updateDto } = formData
    const res = await updateUser(id, updateDto as UserUpdateDTO)
    if (res.code !== '200') {
      ElMessage.error(res.message || '修改失败')
      return
    }
    ElMessage.success('修改成功')
  } else {
    // 新建
    const res = await createUser(formData)
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
  loadData()
})
</script>

<template>
  <div class="user-list-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="用户名/真实姓名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="searchForm.deptId" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="item in deptOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户列表</span>
          <el-button type="primary" @click="handleCreate">+ 新建用户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border style="width: 100%">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="部门" width="120">
          <template #default="{ row }">
            {{ getDeptLabel(row.deptId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <UserFormDialog
      v-model:visible="dialogVisible"
      :user="currentUser"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.user-list-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
