import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

interface ApiResponse<T = unknown> {
  code: string | number
  message: string
  data: T
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['token'] = token
    }
    return config
  },
  (error: unknown) => Promise.reject(error),
)

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    if (res.code !== '200' && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
    }

    // 返回响应体数据，业务层自行判断 code
    return res as unknown as AxiosResponse<ApiResponse>
  },
  (error: unknown) => {
    let msg = '请求失败'

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
        localStorage.removeItem('token')
        if (window.location.hash !== '#/login') {
          window.location.hash = '#/login'
        }
      }
    }

    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
)

export default request
