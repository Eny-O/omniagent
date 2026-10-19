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

  // el-select 只需 {value,label}，直接透传后端 VO（value=数据库主键 id；来源 value=编码字符串）
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

  /**
   * 状态标签颜色：保留原硬编码语义（仅显示用）。
   * 后续可由后端 StatusOptionVO 扩展 tagType 字段驱动，做到完全动态。
   */
  const getStatusTagType = (
    statusId: number | null,
  ): 'info' | 'warning' | 'primary' | 'success' | 'danger' => {
    const map: Record<number, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
      1: 'info',
      2: 'warning',
      3: 'primary',
      4: 'success',
      5: 'info',
      6: 'info',
    }
    if (statusId == null) return 'info'
    return map[statusId] || 'info'
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
    getStatusTagType,
  }
})
