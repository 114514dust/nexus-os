import axios, { type AxiosRequestConfig } from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10000
})

// 统一解包：后端返回 { code, message, data }，这里只把 data 抛给调用方
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      const body = error.response.data
      const msg = body?.message || (status >= 500 ? '服务器开小差了，请稍后重试' : '请求失败')
      return Promise.reject(new Error(msg))
    }

    // 无响应：区分 超时 / 主动取消 / 真正连不上，避免一律提示"网络连接失败"
    const code = error.code as string | undefined
    if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') {
      const raw = (error.config?.timeout ?? http.defaults.timeout ?? 0) as number
      const seconds = Math.round(raw / 1000)
      return Promise.reject(new Error(`请求超时（${seconds} 秒），模型响应较慢，请稍后重试`))
    }
    if (code === 'ERR_CANCELED') {
      return Promise.reject(new Error('请求已取消'))
    }
    return Promise.reject(
      new Error('无法连接后端服务，请确认服务是否已启动（默认 http://localhost:8080）')
    )
  }
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request(config)
  return res as unknown as T
}
