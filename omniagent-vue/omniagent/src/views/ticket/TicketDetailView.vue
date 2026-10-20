<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  TicketAssignDTO,
  TicketComment,
  TicketCommentCreateDTO,
  TicketHistory,
  TicketVO,
} from '@/types/ticket'
import {
  addComment,
  assignTicket,
  getTicketById,
  getTicketHistory,
  listComments,
  transferDepartment,
  updateTicketStatus,
  withdrawTicket,
} from '@/api/ticket'
import { pageUsers } from '@/api/user'
import { listAllDepartments } from '@/api/department'
import type { UserPageVO } from '@/types/user'
import type { DepartmentVO } from '@/types/department'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const dictStore = useDictStore()
const userStore = useUserStore()

const ticketId = computed(() => Number(route.params.id))

// 状态正向流转：基于字典 StatusOptionVO 的 sortOrder（后端 ticket_statuses 已含 code 与 sortOrder）
// 下一步 = 当前状态 sortOrder + 1 的状态（线性流：DRAFT→PENDING→PROCESSING→RESOLVED→CLOSED）；
// CANCELLED 的 sortOrder 不承接正向推进，故不会被选中。彻底去掉前端硬编码映射。

const loading = ref(false)
const ticket = ref<TicketVO | null>(null)

// 用户字典（id -> 显示名），用于展示创建人 / 处理人 / 评论作者
const userMap = ref<Record<number, string>>({})

const currentRole = computed(() => userStore.currentRole)
const isReporter = computed(() => currentRole.value === 'REPORTER')
// 终态（CLOSED / CANCELLED）：字典 isTerminal 为准
const isTerminal = computed(() => {
  const s = dictStore.statuses.find((x) => x.value === ticket.value?.statusId)
  return s?.isTerminal === true
})

// 前端权限闸门（真实鉴权在后端；调试期 role 为 null 时全部放开以便联调）
const canProcess = computed(() => currentRole.value === null || !isReporter.value)
const canWithdraw = computed(
  () =>
    currentRole.value === null ||
    ((isReporter.value &&
      !isTerminal.value &&
      ticket.value?.statusId === 1) ||
      currentRole.value === 'ADMIN'),
)
const canAssign = computed(
  () => currentRole.value === null || currentRole.value === 'SUPERVISOR' || currentRole.value === 'ADMIN',
)
const canComment = computed(
  () => currentRole.value === null || (!isReporter.value && !isTerminal.value),
)
// 转部门权限闸门（真实鉴权在后端 assertCanTransfer）：主管 / 管理员可见，终态工单不可
const canTransfer = computed(
  () =>
    currentRole.value === null ||
    ((currentRole.value === 'SUPERVISOR' || currentRole.value === 'ADMIN') && !isTerminal.value),
)

// 状态正向推进目标（字典驱动，无硬编码）
const currentStatus = computed(() =>
  dictStore.statuses.find((s) => s.value === ticket.value?.statusId) || null,
)
const nextStatus = computed(() => {
  const cur = currentStatus.value
  if (!cur || cur.sortOrder == null) return null
  const curSortOrder: number = cur.sortOrder
  return dictStore.statuses.find((s) => s.sortOrder === curSortOrder + 1) || null
})
const nextStatusCode = computed(() => nextStatus.value?.code ?? null)
const nextStatusLabel = computed(() => nextStatus.value?.label ?? '')

// 正向推进按钮可见闸门：DRAFT 态不允许正向推进——草稿只能由 SUPERVISOR/ADMIN 通过“派单”进入 PENDING，
// 若直接“推进至 待处理”会得到 status=PENDING 但 assignee 为空，导致被指派 HANDLER 看不到，流程断链。
const canAdvance = computed(
  () =>
    canProcess.value &&
    !!nextStatusCode.value &&
    currentStatus.value?.code !== 'DRAFT',
)

const activeTab = ref('overview')

const loadTicket = async () => {
  loading.value = true
  try {
    const res = await getTicketById(ticketId.value)
    if (res.code !== '200') {
      ElMessage.error(res.message || '加载失败')
      return
    }
    ticket.value = res.data
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  try {
    const res = await pageUsers({ pageNum: 1, pageSize: 200 })
    if (res.code === '200') {
      const m: Record<number, string> = {}
      res.data.records.forEach((u: UserPageVO) => {
        m[u.id] = u.realName || u.username
      })
      userMap.value = m
    }
  } catch {
    // 名称解析失败仅回退为展示 id，不影响主流程
  }
}

const userName = (id: number | null) =>
  id == null ? '-' : userMap.value[id] || `用户${id}`

// 部门字典（id -> 名称）与层级下拉选项，用于展示受理部门名称 + 转部门时选择目标部门
const allDepartments = ref<DepartmentVO[]>([])
const deptMap = ref<Record<number, string>>({})
const deptOptions = computed(() => {
  const build = (list: DepartmentVO[], depth = 0): { value: number; label: string }[] => {
    const r: { value: number; label: string }[] = []
    list.forEach((d) => {
      const prefix = depth > 0 ? '　'.repeat(depth) + '└ ' : ''
      r.push({ value: d.id, label: `${prefix}${d.deptName}` })
      if (d.children) r.push(...build(d.children, depth + 1))
    })
    return r
  }
  return build(allDepartments.value)
})
const loadDepartments = async () => {
  try {
    const res = await listAllDepartments()
    if (res.code === '200') {
      allDepartments.value = res.data
      const flat: Record<number, string> = {}
      const walk = (l: DepartmentVO[]) =>
        l.forEach((d) => {
          flat[d.id] = d.deptName
          if (d.children) walk(d.children)
        })
      walk(res.data)
      deptMap.value = flat
    }
  } catch {
    // 部门解析失败仅回退为展示 id，不影响主流程
  }
}
const deptName = (id: number | null) => (id == null ? '-' : deptMap.value[id] || `部门${id}`)

// 状态推进
const handleAdvance = async () => {
  const code = nextStatusCode.value
  if (!code) return
  try {
    await ElMessageBox.confirm(`确定将工单推进至「${nextStatusLabel.value}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  const res = await updateTicketStatus(ticketId.value, code)
  if (res.code !== '200') return
  ElMessage.success('状态已更新')
  await loadTicket()
}

// 撤回（报障人未处理前 / 管理员）
const handleWithdraw = async () => {
  try {
    await ElMessageBox.confirm('确定撤回该工单吗？撤回后状态将变为已取消。', '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  const res = await withdrawTicket(ticketId.value)
  if (res.code !== '200') return
  ElMessage.success('工单已撤回')
  await loadTicket()
}

// 派单弹窗
const assignVisible = ref(false)
const assignForm = reactive<{ assigneeId: number | null; priorityId: number | null; remark: string }>({
  assigneeId: null,
  priorityId: null,
  remark: '',
})
const userOptions = ref<UserPageVO[]>([])
const openAssign = async () => {
  assignForm.assigneeId = null
  assignForm.priorityId = ticket.value?.priorityId ?? null
  assignForm.remark = ''
  const res = await pageUsers({ pageNum: 1, pageSize: 200 })
  if (res.code === '200') userOptions.value = res.data.records
  assignVisible.value = true
}
const submitAssign = async () => {
  if (!assignForm.assigneeId) {
    ElMessage.warning('请选择处理人')
    return
  }
  const dto: TicketAssignDTO = {
    assigneeId: assignForm.assigneeId,
    priorityId: assignForm.priorityId,
    remark: assignForm.remark || undefined,
  }
  const res = await assignTicket(ticketId.value, dto)
  if (res.code !== '200') return
  ElMessage.success('派单成功')
  assignVisible.value = false
  await loadTicket()
}

// 转部门弹窗（主管 / 管理员）
const transferVisible = ref(false)
const transferForm = reactive<{ newDeptId: number | null }>({ newDeptId: null })
const openTransfer = () => {
  transferForm.newDeptId = null
  transferVisible.value = true
}
const submitTransfer = async () => {
  if (!transferForm.newDeptId) {
    ElMessage.warning('请选择目标部门')
    return
  }
  // 调 Batch2 后端 POST /api/tickets/{id}/transfer-department；跨部门会清原指派（后端已保证）
  const res = await transferDepartment(ticketId.value, transferForm.newDeptId)
  if (res.code !== '200') return
  ElMessage.success('已转部门')
  if (res.data) ticket.value = res.data // 直接刷新为后端返回的最新工单（含新部门名）
  transferVisible.value = false
}

// 操作历史
const historyLoading = ref(false)
const histories = ref<TicketHistory[]>([])
const OP_LABEL: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  ASSIGN: '派单',
  STATUS_CHANGE: '状态变更',
  WITHDRAW: '撤回',
  DELETE: '删除',
}
const loadHistory = async () => {
  historyLoading.value = true
  try {
    const res = await getTicketHistory(ticketId.value)
    if (res.code === '200') histories.value = res.data
  } finally {
    historyLoading.value = false
  }
}

// 评论 / 备注
const commentLoading = ref(false)
const comments = ref<TicketComment[]>([])
const commentForm = reactive<{ content: string; isInternal: boolean }>({
  content: '',
  isInternal: false,
})
const loadComments = async () => {
  commentLoading.value = true
  try {
    const res = await listComments(ticketId.value)
    if (res.code === '200') comments.value = res.data
  } finally {
    commentLoading.value = false
  }
}
const submitComment = async () => {
  if (!commentForm.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  const dto: TicketCommentCreateDTO = {
    content: commentForm.content.trim(),
    isInternal: commentForm.isInternal,
  }
  const res = await addComment(ticketId.value, dto)
  if (res.code !== '200') return
  ElMessage.success('评论已提交')
  commentForm.content = ''
  commentForm.isInternal = false
  await loadComments()
}

const onTabChange = (name: string) => {
  if (name === 'history' && histories.value.length === 0) loadHistory()
  if (name === 'comments' && comments.value.length === 0) loadComments()
}

// 返回：来自「我的报障」(from=reporter) 则回该页，否则回「工单管理」
// 这样 REPORTER / ADMIN 从「我的报障」进详情后不会掉进管理视角
const goBack = () => {
  if (route.query.from === 'reporter') {
    router.push('/reporter-tickets')
  } else {
    router.push('/tickets')
  }
}

onMounted(async () => {
  await dictStore.loadDicts()
  await Promise.all([loadTicket(), loadUsers(), loadDepartments()])
})
</script>

<template>
  <div class="ticket-detail-container" v-loading="loading">
    <!-- 头部 -->
    <div class="detail-header">
      <el-button link type="primary" @click="goBack">← 返回列表</el-button>
      <span v-if="ticket" class="header-no">{{ ticket.ticketNo }}</span>
      <el-tag
        v-if="ticket"
        :type="dictStore.getStatusTagType(ticket.statusId)"
        size="small"
      >
        {{ dictStore.getStatusLabel(ticket.statusId) }}
      </el-tag>
    </div>

    <!-- 操作栏（按角色/状态显隐；真实鉴权在后端） -->
    <div v-if="ticket" class="action-bar">
      <el-button
        v-if="canAdvance"
        type="primary"
        @click="handleAdvance"
      >
        {{ nextStatusCode === 'PROCESSING' ? '接受处理' : '推进至 ' + nextStatusLabel }}
      </el-button>
      <el-button v-if="canWithdraw" type="warning" @click="handleWithdraw">
        撤回工单
      </el-button>
      <el-button v-if="canAssign" type="success" @click="openAssign">
        派单
      </el-button>
      <el-button v-if="canTransfer" type="warning" @click="openTransfer">
        转部门
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 概览 -->
      <el-tab-pane label="概览" name="overview">
        <el-descriptions v-if="ticket" :column="2" border>
          <el-descriptions-item label="标题" :span="2">
            {{ ticket.title }}
          </el-descriptions-item>
          <el-descriptions-item label="分类">
            {{ dictStore.getCategoryLabel(ticket.categoryId) }}
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            {{ dictStore.getPriorityLabel(ticket.priorityId) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ dictStore.getSourceLabel(ticket.source) }}</el-descriptions-item>
          <el-descriptions-item label="受理部门">{{ deptName(ticket.deptId) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">
            {{ userName(ticket.creatorId) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前处理人">
            {{ userName(ticket.currentAssigneeId) }}
          </el-descriptions-item>
          <el-descriptions-item label="联系人手机">
            {{ ticket.contactPhone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系人邮箱">
            {{ ticket.contactEmail || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="位置">
            {{ ticket.location || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="期望解决时间">
            {{ ticket.expectedResolveAt || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="解决时间">
            {{ ticket.resolvedAt || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="关闭时间">
            {{ ticket.closedAt || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ ticket.createdAt }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ ticket.updatedAt }}
          </el-descriptions-item>
          <el-descriptions-item label="报障描述" :span="2">
            {{ ticket.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- 操作历史 -->
      <el-tab-pane label="操作历史" name="history">
        <el-timeline v-loading="historyLoading">
          <el-timeline-item
            v-for="h in histories"
            :key="h.id"
            :timestamp="h.createdAt"
            placement="top"
          >
            <div class="history-item">
              <el-tag size="small" type="info">
                {{ OP_LABEL[h.operation] || h.operation }}
              </el-tag>
              <span class="history-op">操作人：{{ userName(h.operatorId) }}</span>
              <span v-if="h.fieldName" class="history-field">
                {{ h.fieldName }}：{{ h.oldValue || '空' }} → {{ h.newValue || '空' }}
              </span>
              <span v-if="h.remark" class="history-remark">备注：{{ h.remark }}</span>
            </div>
          </el-timeline-item>
          <el-empty v-if="!historyLoading && histories.length === 0" description="暂无操作记录" />
        </el-timeline>
      </el-tab-pane>

      <!-- 评论 / 备注 -->
      <el-tab-pane label="评论 / 备注" name="comments">
        <div v-loading="commentLoading">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-meta">
              <el-tag
                size="small"
                :type="c.authorType === 'OPERATOR' ? 'primary' : c.authorType === 'SYSTEM' ? 'info' : 'success'"
              >
                {{ c.authorType === 'OPERATOR' ? '处理人' : c.authorType === 'SYSTEM' ? '系统' : '用户' }}
              </el-tag>
              <el-tag v-if="c.isInternal" size="small" type="warning">内部备注</el-tag>
              <span class="comment-author">{{ userName(c.authorId) }}</span>
              <span class="comment-time">{{ c.createdAt }}</span>
            </div>
            <div class="comment-content">{{ c.content }}</div>
          </div>
          <el-empty v-if="!commentLoading && comments.length === 0" description="暂无评论" />
        </div>

        <!-- 评论输入（仅处理角色可写，终态禁用） -->
        <div v-if="canComment" class="comment-form">
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="3"
            placeholder="填写处理状况 / 备注…"
          />
          <div class="comment-form-actions">
            <el-switch v-model="commentForm.isInternal" active-text="内部备注" />
            <el-button type="primary" @click="submitComment">提交</el-button>
          </div>
        </div>
        <el-alert
          v-else-if="isTerminal"
          type="info"
          :closable="false"
          title="工单已结束，不可再评论"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          title="报障人仅可查看公开备注，不可发表评论"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 派单弹窗 -->
    <el-dialog v-model="assignVisible" title="派单" width="420px">
      <el-form label-width="80px">
        <el-form-item label="优先级(终审)">
          <el-select
            v-model="assignForm.priorityId"
            placeholder="请确认/调整优先级"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in dictStore.priorityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人" required>
          <el-select
            v-model="assignForm.assigneeId"
            filterable
            placeholder="请选择处理人"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.realName || u.username}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确定派单</el-button>
      </template>
    </el-dialog>

    <!-- 转部门弹窗 -->
    <el-dialog v-model="transferVisible" title="转部门" width="420px">
      <el-form label-width="80px">
        <el-form-item label="目标部门" required>
          <el-select
            v-model="transferForm.newDeptId"
            placeholder="请选择目标部门"
            style="width: 100%"
          >
            <el-option
              v-for="item in deptOptions.filter((o) => o.value !== ticket?.deptId)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确定转部门</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-detail-container {
  padding: 20px;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.header-no {
  font-weight: 600;
  font-size: 16px;
}
.action-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
}
.history-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.history-field {
  color: #606266;
}
.history-remark {
  color: #909399;
}
.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.comment-author {
  font-weight: 500;
}
.comment-time {
  color: #909399;
  font-size: 12px;
}
.comment-content {
  white-space: pre-wrap;
  color: #303133;
}
.comment-form {
  margin-top: 16px;
}
.comment-form-actions {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
