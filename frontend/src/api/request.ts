import axios, { type AxiosInstance } from 'axios'

/** 后端统一响应体 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 响应拦截：解包 Result，非 0 抛错
http.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code !== 0) {
        return Promise.reject(new Error(res.message || '请求失败'))
      }
      return res.data as never
    }
    return response.data as never
  },
  (error) => Promise.reject(error)
)

export default http
