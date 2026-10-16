<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TicketPageVO, TicketForm, TicketUpdateDTO } from '@/types/ticket'
import {
  pageTickets,
  deleteTicket,
  createTicket,
  updateTicket,
} from '@/api/ticket'
import TicketFormDialog from './TicketFormDialog.vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  statusId: null as number | null,
  priorityId: null as number | null,
  categoryId: null as number | null,
})

// 表格数据
const loading = ref(false)
const tableData = ref<TicketPageVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 弹窗
const dialogVisible = ref(false)
const currentTicket = ref<TicketForm | undefined>(undefined)

// 状态、优先级、分类选项
const statusOptions = [
  { label: '草稿', value: 1 },
  { label: '待处理', value: 2 },
  { label: '处理中', value: 3 },
  { label: '已解决', value: 4 },
  { label: '已关闭', value: 5 },
]

const priorityOptions = [
  { label: '高', value: 3 },
  { label: '中', value: 2 },
  { label: '低', value: 1 },
]

const categoryOptions = [
  { label: '硬件故障', value: 1 },
  { label: '软件问题', value: 2 },
  { label: '网络问题', value: 3 },
  { label: '其他', value: 4 },
]

const getStatusTagType = (statusId: number) => {
  const map: Record<number, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
    1: 'info',
    2: 'warning',
    3: 'primary',
    4: 'success',
    5: 'info',
  }
  return map[statusId] || 'info'
}

const getStatusLabel = (statusId: number) => {
  return statusOptions.find((item) => item.value === statusId)?.label || '未知'
}

const getPriorityLabel = (priorityId: number | null) => {
  if (!priorityId) return '-'
  return priorityOptions.find((item) => item.value === priorityId)?.label || '未知'
}

const getCategoryLabel = (categoryId: number | null) => {
  if (!categoryId) return '-'
  return categoryOptions.find((item) => item.value === categoryId)?.label || '未知'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await pageTickets({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      statusId: searchForm.statusId,
      priorityId: searchForm.priorityId,
      categoryId: searchForm.categoryId,
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
  searchForm.statusId = null
  searchForm.priorityId = null
  searchForm.categoryId = null
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
  currentTicket.value = undefined
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: TicketPageVO) => {
  currentTicket.value = {
    id: row.id,
    title: row.title,
    description: '',
    categoryId: row.categoryId,
    priorityId: row.priorityId,
    currentAssigneeId: row.currentAssigneeId,
    source: row.source,
    contactPhone: '',
    contactEmail: '',
    location: '',
    expectedResolveAt: null,
  } as TicketForm
  dialogVisible.value = true
}

// 删除
const handleDelete = (row: TicketPageVO) => {
  ElMessageBox.confirm(`确定删除工单「${row.title}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      const res = await deleteTicket(row.id)
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
const handleSubmit = async (formData: TicketForm) => {
  if (formData.id) {
    // 编辑：转换为更新 DTO，排除 id 和 source
    const { id, source, ...updateDto } = formData
    const res = await updateTicket(id, updateDto as TicketUpdateDTO)
    if (res.code !== '200') {
      ElMessage.error(res.message || '修改失败')
      return
    }
    ElMessage.success('修改成功')
  } else {
    // 新建
    const res = await createTicket(formData)
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
  <div class="ticket-list-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="标题/工单号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.statusId" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select
            v-model="searchForm.priorityId"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="searchForm.categoryId"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="item in categoryOptions"
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
          <span>工单列表</span>
          <el-button type="primary" @click="handleCreate">+ 新建工单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border style="width: 100%">
        <el-table-column prop="ticketNo" label="工单编号" width="160" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">
            {{ getCategoryLabel(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            {{ getPriorityLabel(row.priorityId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.statusId)">
              {{ getStatusLabel(row.statusId) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">
            {{ row.currentAssigneeId ? `用户${row.currentAssigneeId}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="80" />
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

    <TicketFormDialog
      v-model:visible="dialogVisible"
      :ticket="currentTicket"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.ticket-list-container {
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
