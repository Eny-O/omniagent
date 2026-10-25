import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getAllDicts,
  type CategoryOptionVO,
  type PriorityOptionVO,
  type SourceOptionVO,
  type StatusOptionVO,
} from '@/api/dict'
import { ElMessage } from 'element-plus'

/**
 * 工单字典 Store
 * <p>
 * 登录后从后端动态加载分类/状态/优先级/来源下拉选项，替换前端硬编码。
 * loadDicts 幂等：已加载或正在加载则跳过，供列表页与弹窗安全重复调用。
 */
export const useDictStore = defineStore('dict', () => {
  const categories = ref<CategoryOptionVO[]>([])
  const statuses = ref<StatusOptionVO[]>([])
  const priorities = ref<PriorityOptionVO[]>([])
  const sources = ref<SourceOptionVO[]>([])

  const loaded = ref(false)
  const loading = ref(false)

  // el-select 只需 {value,label}，直接透传后端 VO（value=表主键 id；来源 value=编码字符串）。
  // 状态标识统一用 ticket_statuses.id：tickets.status_id 存的就是 id（如 TO_ASSIGN 占 id=7，sort_order=2 仅用于显示排序）。
  const categoryOptions = computed(() => categories.value)
  const statusOptions = computed(() => statuses.value)
  const priorityOptions = computed(() => priorities.value)
  const sourceOptions = computed(() => sources.value)

  /**
   * 加载全部字典（幂等）
   */
  const loadDicts = async (): Promise<boolean> => {
    if (loaded.value || loading.value) return loaded.value
    loading.value = true
    try {
      const res = await getAllDicts()
      if (res.code !== '200') {
        ElMessage.error(res.message || '字典加载失败')
        return false
      }
      categories.value = res.data.categories ?? []
      statuses.value = res.data.statuses ?? []
      priorities.value = res.data.priorities ?? []
      sources.value = res.data.sources ?? []
      loaded.value = true
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 通用：根据 id 在选项列表中查 label，未命中返回占位
   */
  const findLabel = (
    list: { value: number; label: string }[],
    id: number | null,
  ): string => {
    if (id == null) return '-'
    return list.find((item) => item.value === id)?.label || '未知'
  }

  const getCategoryLabel = (id: number | null) => findLabel(categories.value, id)
  const getPriorityLabel = (id: number | null) => findLabel(priorities.value, id)
  const getStatusLabel = (id: number | null) => findLabel(statuses.value, id)
  // 来源字典 value 为编码字符串（WEB/AGENT…），无法复用 number 版的 findLabel
  const getSourceLabel = (code: string | null): string => {
    if (!code) return '-'
    return sources.value.find((item) => item.value === code)?.label || code
  }

  /**
   * 状态标签配色：按语义为每个状态独立配色（el-tag 内置 type 仅 5 色，不够 7 个状态区分，
   * 故返回内联样式覆盖 el-tag 的文字/背景/边框色，浅色主题下均为"浅底深字"）。
   * 键 = 状态字典 id（tickets.status_id 存储值）：
   * DRAFT=1 / PENDING=2 / PROCESSING=3 / RESOLVED=4 / CLOSED=5 / CANCELLED=6 / TO_ASSIGN=7
   */
  const getStatusTagStyle = (
    statusId: number | null,
  ): { color: string; backgroundColor: string; borderColor: string } => {
    type TagStyle = { color: string; backgroundColor: string; borderColor: string }
    const DRAFT_STYLE: TagStyle = {
      color: '#909399',
      backgroundColor: '#f4f4f5',
      borderColor: '#d3d4d6',
    }
    const map: Record<number, TagStyle> = {
      1: DRAFT_STYLE, // DRAFT 草稿：灰（未提交）
      2: { color: '#409eff', backgroundColor: '#ecf5ff', borderColor: '#b3d8ff' }, // PENDING 待处理：蓝（已派单等待处理）
      3: { color: '#722ed1', backgroundColor: '#f4efff', borderColor: '#d3c0f5' }, // PROCESSING 处理中：紫（进行中）
      4: { color: '#67c23a', backgroundColor: '#f0f9eb', borderColor: '#c2e7b0' }, // RESOLVED 已解决：绿（已给出方案）
      5: { color: '#515a6e', backgroundColor: '#e4e7ed', borderColor: '#c0c4cc' }, // CLOSED 已关闭：深灰（归档完结）
      6: { color: '#f56c6c', backgroundColor: '#fef0f0', borderColor: '#fbc4c4' }, // CANCELLED 已取消：红（作废）
      7: { color: '#e6a23c', backgroundColor: '#fdf6ec', borderColor: '#f5dab1' }, // TO_ASSIGN 待派单：橙（待主管派单）
    }
    if (statusId == null) return DRAFT_STYLE
    return map[statusId] ?? DRAFT_STYLE
  }

  return {
    categories,
    statuses,
    priorities,
    sources,
    loaded,
    categoryOptions,
    statusOptions,
    priorityOptions,
    sourceOptions,
    loadDicts,
    getCategoryLabel,
    getPriorityLabel,
    getStatusLabel,
    getSourceLabel,
    getStatusTagStyle,
  }
})
