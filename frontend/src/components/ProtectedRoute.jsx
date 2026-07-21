import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'

export default function ProtectedRoute({ children }) {
  const { t } = useTranslation('common')
  const { user, loading } = useAuth()
  const location = useLocation()
  const token = localStorage.getItem('token')

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-gray-400">
        {t('loading')}
      </div>
    )
  }

  if (!token || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}
