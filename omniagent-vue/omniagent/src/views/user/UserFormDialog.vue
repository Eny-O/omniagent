<script setup lang="ts">
import { ref, watch } from 'vue'
import type { UserForm } from '@/types/user'
import { getUserById } from '@/api/user'

const props = defineProps<{
  visible: boolean
  user?: UserForm
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit', value: UserForm): void
}>()

const formRef = ref()
const formData = ref<UserForm>({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  deptId: null,
  status: 1,
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

const validateUsername = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value || value.trim() === '') {
    callback(new Error('请输入用户名'))
    return
  }
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(value)) {
    callback(new Error('用户名为3-20位字母、数字或下划线'))
    return
  }
  callback()
}

const validatePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (props.user?.id) {
    // 编辑时密码不校验
    callback()
    return
  }
  if (!value || value.trim() === '') {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 6 || value.length > 20) {
    callback(new Error('密码长度为6-20位'))
    return
  }
  callback()
}

const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const deptOptions = [
  { label: '技术部', value: 1 },
  { label: '运维部', value: 2 },
  { label: '客服部', value: 3 },
]

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      if (props.user?.id) {
        // 编辑：调用详情接口补全数据
        const res = await getUserById(props.user.id)
        if (res.code === '200') {
          formData.value = { ...res.data, password: '' }
        } else {
          formData.value = { ...props.user }
        }
      } else if (props.user) {
        formData.value = { ...props.user }
      } else {
        formData.value = {
          username: '',
          password: '',
          realName: '',
          email: '',
          phone: '',
          deptId: null,
          status: 1,
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
    :title="user?.id ? '编辑用户' : '新建用户'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="formData.username"
              placeholder="3-20位字母/数字/下划线"
              :disabled="!!user?.id"
              maxlength="20"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="formData.password"
              type="password"
              placeholder="6-20位密码"
              show-password
              :disabled="!!user?.id"
              maxlength="20"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="真实姓名">
            <el-input v-model="formData.realName" placeholder="请输入真实姓名" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="formData.status" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="item in statusOptions"
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
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="formData.email" placeholder="请输入邮箱" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="formData.phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="部门">
        <el-select v-model="formData.deptId" placeholder="请选择" clearable style="width: 100%">
          <el-option
            v-for="item in deptOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
