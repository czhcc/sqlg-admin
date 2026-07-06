import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
      return Promise.reject(new Error('未登录或登录已过期'))
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(msg))
  }
)

export default client
