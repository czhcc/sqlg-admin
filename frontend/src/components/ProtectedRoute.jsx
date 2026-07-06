import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  const token = localStorage.getItem('token')

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-gray-400">
        加载中...
      </div>
    )
  }

  if (!token || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}
