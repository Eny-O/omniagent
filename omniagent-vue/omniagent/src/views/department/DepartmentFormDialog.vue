<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { DepartmentForm, DepartmentVO } from '@/types/department'

const props = defineProps<{
  visible: boolean
  department?: DepartmentForm
  allDepartments: DepartmentVO[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit', value: DepartmentForm): void
}>()

const formRef = ref()
const formData = ref<DepartmentForm>({
  deptCode: '',
  deptName: '',
  parentId: null,
  sortOrder: 0,
})

const rules = {
  deptCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { min: 2, max: 64, message: '长度为2-64个字符', trigger: 'blur' },
  ],
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { min: 2, max: 128, message: '长度为2-128个字符', trigger: 'blur' },
  ],
}

// 上级部门下拉选项：平铺展示，并缩进体现层级
const parentOptions = computed(() => {
  const buildOptions = (list: DepartmentVO[], depth = 0) => {
    const result: { value: number; label: string }[] = []
    list.forEach((item) => {
      const prefix = depth > 0 ? '　'.repeat(depth) + '└ ' : ''
      result.push({
        value: item.id,
        label: `${prefix}${item.deptName}（${item.deptCode}）`,
      })
      if (item.children && item.children.length > 0) {
        result.push(...buildOptions(item.children, depth + 1))
      }
    })
    return result
  }

  // 先组装成树，再平铺成选项
  const map = new Map<number, DepartmentVO>()
  const roots: DepartmentVO[] = []
  props.allDepartments.forEach((item) => {
    map.set(item.id, { ...item, children: [] })
  })
  props.allDepartments.forEach((item) => {
    const node = map.get(item.id)!
    if (item.parentId && map.has(item.parentId)) {
      const parent = map.get(item.parentId)!
      parent.children = parent.children || []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })

  const top = { value: 0 as number, label: '无（根部门）' }
  return [top, ...buildOptions(roots)]
})

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.department) {
        formData.value = {
          id: props.department.id,
          deptCode: props.department.deptCode,
          deptName: props.department.deptName,
          parentId: props.department.parentId ?? null,
          sortOrder: props.department.sortOrder ?? 0,
        }
      } else {
        formData.value = {
          deptCode: '',
          deptName: '',
          parentId: null,
          sortOrder: 0,
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
      // parentId 为 0 时转换为 null，与后端约定一致
      const submitData = { ...formData.value }
      if (submitData.parentId === 0) {
        submitData.parentId = null
      }
      emit('submit', submitData)
    }
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="department?.id ? '编辑部门' : '新建部门'"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="部门编码" prop="deptCode">
        <el-input
          v-model="formData.deptCode"
          placeholder="如：DEPT_IT"
          maxlength="64"
          :disabled="!!department?.id"
        />
      </el-form-item>

      <el-form-item label="部门名称" prop="deptName">
        <el-input v-model="formData.deptName" placeholder="请输入部门名称" maxlength="128" />
      </el-form-item>

      <el-form-item label="上级部门">
        <el-select
          v-model="formData.parentId"
          placeholder="请选择上级部门"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="item in parentOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="排序号">
        <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
