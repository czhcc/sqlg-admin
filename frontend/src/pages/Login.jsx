import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Database, Lock, User } from 'lucide-react'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(username, password)
      const to = location.state?.from?.pathname || '/'
      navigate(to, { replace: true })
    } catch (err) {
      setError(err.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-800 to-slate-900">
      <div className="w-full max-w-md rounded-2xl bg-white p-8 shadow-2xl">
        <div className="mb-8 flex flex-col items-center">
          <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-600 text-white">
            <Database size={32} />
          </div>
          <h1 className="text-2xl font-bold text-gray-800">图数据库管理平台</h1>
          <p className="mt-1 text-sm text-gray-500">Graph Database Management</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">用户名</label>
            <div className="relative">
              <User className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                placeholder="请输入用户名"
                autoComplete="username"
                required
              />
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">密码</label>
            <div className="relative">
              <Lock className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
                placeholder="请输入密码"
                autoComplete="current-password"
                required
              />
            </div>
          </div>

          {error && (
            <div className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{error}</div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-indigo-600 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? '登录中...' : '登 录'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-gray-400">
          初始账号: admin / 123456
        </p>
      </div>
    </div>
  )
}
