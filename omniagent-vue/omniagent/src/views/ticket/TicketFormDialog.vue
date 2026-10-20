<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { TicketForm } from '@/types/ticket'
import { getTicketById } from '@/api/ticket'
import { listAllDepartments } from '@/api/department'
import { useDictStore } from '@/stores/dict'
import { useUserStore } from '@/stores/user'
import type { DepartmentVO } from '@/types/department'

// 字典下拉选项（分类/优先级/来源）均由 dict store 动态提供
const dictStore = useDictStore()
// 当前登录用户（用于新建时默认预填其所属部门）
const userStore = useUserStore()

// 部门树（用于受理部门下拉），打开弹窗时按需加载
const allDepartments = ref<DepartmentVO[]>([])

// 将部门树平铺为带层级缩进的下拉选项（沿用 DepartmentFormDialog 的平铺思路）
const deptOptions = computed(() => {
  const build = (list: DepartmentVO[], depth = 0): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = []
    list.forEach((item) => {
      const prefix = depth > 0 ? '　'.repeat(depth) + '└ ' : ''
      result.push({ value: item.id, label: `${prefix}${item.deptName}` })
      if (item.children && item.children.length > 0) {
        result.push(...build(item.children, depth + 1))
      }
    })
    return result
  }
  // 先组装成树，再平铺成选项
  const map = new Map<number, DepartmentVO>()
  const roots: DepartmentVO[] = []
  allDepartments.value.forEach((item) => map.set(item.id, { ...item, children: [] }))
  allDepartments.value.forEach((item) => {
    const node = map.get(item.id)!
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  return build(roots)
})

// 受理部门是否可编辑：新建(true) 可编辑；编辑态仅报障人(reporterMode)可改，其余只读
// （归属变更对主管/管理员走「转部门」按钮）。reporter 模式下编辑入口已被列表 isDraftOrPending 限制为 DRAFT/PENDING，
// 与后端 assertEditable 的「报障人仅未处理前可改」一致。
const deptDisabled = computed(() => {
  if (!props.ticket?.id) return false
  return !props.reporterMode
})

const props = defineProps<{
  visible: boolean
  ticket?: TicketForm
  reporterMode?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit', value: TicketForm): void
}>()

const formRef = ref()
const formData = ref<TicketForm>({
  title: '',
  description: '',
  categoryId: null,
  priorityId: null,
  source: 'WEB',
  deptId: null,
  contactPhone: '',
  contactEmail: '',
  location: '',
  expectedResolveAt: null,
})

const validatePhone = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value || value.trim() === '') {
    callback()
    return
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号码'))
    return
  }
  callback()
}

const validateEmail = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value || value.trim() === '') {
    callback()
    return
  }
  if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value)) {
    callback(new Error('请输入正确的邮箱地址'))
    return
  }
  callback()
}

const rules = {
  title: [{ required: true, message: '请输入工单标题', trigger: 'blur' }],
  contactPhone: [{ validator: validatePhone, trigger: 'blur' }],
  contactEmail: [{ validator: validateEmail, trigger: 'blur' }],
}

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      // 兜底加载字典（幂等：列表页已加载则直接复用）
      await dictStore.loadDicts()
      // 加载部门树（受理部门下拉），失败不影响其他字段
      const deptRes = await listAllDepartments()
      if (deptRes.code === '200') {
        allDepartments.value = deptRes.data
      }
      if (props.ticket?.id) {
        // 编辑：调用详情接口补全数据
        const res = await getTicketById(props.ticket.id)
        if (res.code === '200') {
          formData.value = { ...res.data }
        } else {
          formData.value = { ...props.ticket }
        }
      } else if (props.ticket) {
        formData.value = { ...props.ticket }
      } else {
        formData.value = {
          title: '',
          description: '',
          categoryId: null,
          priorityId: null,
          source: 'WEB',
          deptId: userStore.user?.deptId ?? null,
          contactPhone: '',
          contactEmail: '',
          location: '',
          expectedResolveAt: null,
        }
      }
      formRef.value?.resetFields()
    }
  },
)

const handleClose = () => {
  emit('update:visible', false)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid: boolean) => {
    if (valid) {
      emit('submit', { ...formData.value })
    }
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="ticket?.id ? '编辑工单' : '新建工单'"
    width="600px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="工单标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入工单标题" maxlength="255" show-word-limit />
      </el-form-item>

      <el-form-item label="工单描述">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="3"
          placeholder="请描述具体问题"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="分类">
            <el-select v-model="formData.categoryId" placeholder="请选择" clearable style="width: 100%">
              <el-option
                v-for="item in dictStore.categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级">
            <el-select v-model="formData.priorityId" placeholder="请选择" clearable style="width: 100%">
              <el-option
                v-for="item in dictStore.priorityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="受理部门">
            <el-select
              v-model="formData.deptId"
              placeholder="请选择受理部门"
              clearable
              style="width: 100%"
              :disabled="deptDisabled"
            >
              <el-option
                v-for="item in deptOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col v-if="!reporterMode" :span="12">
          <el-form-item label="来源">
            <el-select v-model="formData.source" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="item in dictStore.sourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系邮箱" prop="contactEmail">
            <el-input v-model="formData.contactEmail" placeholder="请输入联系邮箱" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="位置">
        <el-input v-model="formData.location" placeholder="请输入位置，如 A栋3楼301" />
      </el-form-item>

      <el-form-item label="预计解决时间">
        <el-date-picker
          v-model="formData.expectedResolveAt"
          type="datetime"
          placeholder="请选择预计解决时间"
          style="width: 100%"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
