import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginUserVO } from '@/types/auth'
import { getCurrentUser, login, logout } from '@/api/auth'
import router from '@/router'
import { ElMessage } from 'element-plus'

/**
 * 用户状态 Store
 * <p>
 * 采用 Session/Cookie 方案，浏览器自动管理 Session Cookie，
 * 本 Store 只保存当前登录用户信息，不保存 token。
 */
export const useUserStore = defineStore('user', () => {
  // 当前登录用户信息
  const user = ref<LoginUserVO | null>(null)

  // 是否已登录
  const isLoggedIn = computed(() => user.value !== null)

  // 当前登录用户角色（REPORTER/HANDLER/SUPERVISOR/ADMIN），未登录为 null
  const currentRole = computed(() => user.value?.role ?? null)

  /**
   * 设置当前用户
   */
  const setUser = (value: LoginUserVO | null) => {
    user.value = value
  }

  /**
   * 清除当前用户
   */
  const clearUser = () => {
    user.value = null
  }

  /**
   * 登录
   */
  const doLogin = async (username: string, password: string) => {
    const res = await login({ username, password })
    if (res.code !== '200') {
      ElMessage.error(res.message || '登录失败')
      return false
    }
    user.value = res.data
    ElMessage.success('登录成功')
    return true
  }

  /**
   * 获取当前登录用户信息
   */
  const fetchCurrentUser = async (): Promise<boolean> => {
    try {
      const res = await getCurrentUser()
      if (res.code === '200' && res.data) {
        user.value = res.data
        return true
      }
      user.value = null
      return false
    } catch {
      user.value = null
      return false
    }
  }

  /**
   * 登出
   */
  const doLogout = async () => {
    try {
      await logout()
    } finally {
      user.value = null
      router.push('/login')
    }
  }

  return {
    user,
    isLoggedIn,
    currentRole,
    setUser,
    clearUser,
    doLogin,
    fetchCurrentUser,
    doLogout,
  }
})
