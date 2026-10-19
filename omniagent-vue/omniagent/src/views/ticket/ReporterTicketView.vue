<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TicketPageVO, TicketForm, TicketUpdateDTO } from '@/types/ticket'
import {
  pageTickets,
  createTicket,
  updateTicket,
  withdrawTicket,
} from '@/api/ticket'
import { useDictStore } from '@/stores/dict'
import TicketFormDialog from './TicketFormDialog.vue'

const router = useRouter()
const dictStore = useDictStore()

// 状态判定基于字典 code，避免前端硬编码
const statusCode = (statusId: number | undefined) =>
  dictStore.statusOptions.find((s) => s.value === statusId)?.code
// 报障人仅在「草稿 / 待处理」可编辑或撤回（与后端 assertEditable / withdrawTicket 一致）
const isDraftOrPending = (row: TicketPageVO) =>
  statusCode(row.statusId) === 'DRAFT' || statusCode(row.statusId) === 'PENDING'

// 搜索
const searchForm = reactive({ keyword: '', statusId: null as number | null })
const loading = ref(false)
const tableData = ref<TicketPageVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 弹窗
const dialogVisible = ref(false)
const currentTicket = ref<TicketForm | undefined>(undefined)

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageTickets({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      statusId: searchForm.statusId,
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

const handleSearch = () => {
  pageNum.value = 1
  loadData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.statusId = null
  pageNum.value = 1
  loadData()
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  loadData()
}

const handleCurrentChange = (val: number) => {
  pageNum.value = val
  loadData()
}

// 新建报障（reporter 模式：隐藏处理人 / 来源）
const handleCreate = () => {
  currentTicket.value = undefined
  dialogVisible.value = true
}

// 编辑提交内容（仅草稿 / 待处理）
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

// 查看详情：复用 TicketDetailView（已按角色收敛，reporter 仅见撤回 / 公开备注）
// 带上 from=reporter，使详情页"返回"能回到「我的报障」而非「工单管理」
const goDetail = (row: TicketPageVO) => {
  router.push(`/tickets/${row.id}?from=reporter`)
}

// 撤回 / 取消（仅草稿 / 待处理）
const handleWithdraw = (row: TicketPageVO) => {
  ElMessageBox.confirm(`确定撤回工单「${row.title}」吗？撤回后状态将变为已取消。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      const res = await withdrawTicket(row.id)
      if (res.code !== '200') {
        ElMessage.error(res.message || '撤回失败')
        return
      }
      ElMessage.success('工单已撤回')
      loadData()
    })
    .catch(() => {
      // 取消撤回
    })
}

const handleSubmit = async (formData: TicketForm) => {
  if (formData.id) {
    // 编辑：转换为更新 DTO，排除 id 与 source（来源由后端按创建上下文决定）
    const { id, source, ...updateDto } = formData
    const res = await updateTicket(id, updateDto as TicketUpdateDTO)
    if (res.code !== '200') {
      ElMessage.error(res.message || '修改失败')
      return
    }
    ElMessage.success('修改成功')
  } else {
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

onMounted(async () => {
  await dictStore.loadDicts()
  loadData()
})
</script>

<template>
  <div class="reporter-ticket-container">
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
              v-for="item in dictStore.statusOptions"
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
          <span>我的报障</span>
          <el-button type="primary" @click="handleCreate">+ 新建报障</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border style="width: 100%">
        <el-table-column prop="ticketNo" label="工单编号" width="160" />
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">
            {{ dictStore.getCategoryLabel(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            {{ dictStore.getPriorityLabel(row.priorityId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="dictStore.getStatusTagType(row.statusId)">
              {{ dictStore.getStatusLabel(row.statusId) }}
            </el-tag>
          </el-table-column>
        <el-table-column prop="source" label="来源" width="80" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button
              v-if="isDraftOrPending(row)"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="isDraftOrPending(row)"
              link
              type="warning"
              @click="handleWithdraw(row)"
            >
              撤回
            </el-button>
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
      :reporter-mode="true"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.reporter-ticket-container {
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
