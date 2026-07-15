import { createContext, useContext, useEffect, useState } from 'react'
import * as authApi from '../api/auth'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [permissions, setPermissions] = useState(null)
  const [loading, setLoading] = useState(true)

  const fetchPermissions = (userId) => {
    return authApi.getMyPermissions()
      .then((res) => setPermissions(res.data))
      .catch(() => setPermissions(null))
  }

  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) {
      setLoading(false)
      return
    }
    authApi
      .getUserInfo()
      .then((res) => {
        setUser(res.data)
        return fetchPermissions(res.data.id)
      })
      .catch(() => localStorage.removeItem('token'))
      .finally(() => setLoading(false))
  }, [])

  const login = async (username, password) => {
    const res = await authApi.login(username, password)
    localStorage.setItem('token', res.data.token)
    const infoRes = await authApi.getUserInfo()
    setUser(infoRes.data)
    await fetchPermissions(infoRes.data.id)
    return res.data
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } finally {
      localStorage.removeItem('token')
      setUser(null)
      setPermissions(null)
    }
  }

  const hasMenu = (menuKey) => {
    if (!permissions) return false
    if (permissions.menus?.includes('*')) return true
    return permissions.menus?.includes(menuKey) || false
  }

  const hasOp = (code) => {
    if (!permissions) return false
    if (permissions.operations?.includes('*')) return true
    return permissions.operations?.includes(code) || false
  }

  return (
    <AuthContext.Provider value={{ user, permissions, loading, login, logout, hasMenu, hasOp }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
