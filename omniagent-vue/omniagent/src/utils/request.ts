import axios, {
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken } from '@/utils/token'

interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
}

interface ApiRequest {
  interceptors: {
    request: {
      use(
        onFulfilled?: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig | Promise<InternalAxiosRequestConfig>,
        onRejected?: (error: unknown) => unknown,
      ): number
    }
    response: {
      use(
        onFulfilled?: (response: AxiosResponse<ApiResponse>) => ApiResponse | Promise<ApiResponse>,
        onRejected?: (error: unknown) => unknown,
      ): number
    }
  }
  get<T>(url: string, config?: Record<string, unknown>): Promise<ApiResponse<T>>
  post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<ApiResponse<T>>
  put<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<ApiResponse<T>>
  delete<T>(url: string, config?: Record<string, unknown>): Promise<ApiResponse<T>>
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
}) as unknown as ApiRequest

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 无状态 JWT 方案：统一在请求头携带 Bearer
    const token = getToken()
    if (token) {
      const headers = config.headers as Record<string, unknown>
      headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error: unknown) => Promise.reject(error),
)

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data as ApiResponse
    const code = String(res.code)

    // 会话恢复等静默请求：不弹全局错误提示
    const silent = (response.config as { silent?: boolean }).silent === true
    if (code !== '200' && !silent) {
      ElMessage.error(res.message || '请求失败')
    }

    // 返回响应体数据，业务层自行判断 code
    return res
  },
  (error: unknown) => {
    let msg = '请求失败'

    // 静默请求：不弹全局错误提示（如路由守卫的会话恢复 401）
    const silent =
      error &&
      typeof error === 'object' &&
      'config' in error &&
      (error.config as { silent?: boolean } | undefined)?.silent === true

    if (
      error &&
      typeof error === 'object' &&
      'response' in error &&
      error.response &&
      typeof error.response === 'object'
    ) {
      const data = (error.response as { data?: unknown }).data

      if (data && typeof data === 'object' && 'message' in data) {
        msg = String((data as { message: unknown }).message)
      }

      if ('status' in error.response && error.response.status === 401) {
        // 未登录或 Session 过期，跳转到登录页
        if (router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
      }
    }

    if (!silent) ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
)

export default request
