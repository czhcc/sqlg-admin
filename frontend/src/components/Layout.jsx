import { useState, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { updateProfile, changePassword } from '../api/auth'
import LanguageSwitcher from './LanguageSwitcher'
import {
  Database,
  Share2,
  CircleDot,
  Minus,
  Table,
  GitFork,
  Network,
  TerminalSquare,
  ArrowLeftRight,
  ScrollText,
  LogOut,
  Boxes,
  Tags,
  Users,
  User,
  ShieldCheck,
  KeyRound,
  ClipboardList,
  LogIn,
  ChevronDown,
  ChevronRight,
  X,
  Save,
  Globe,
} from 'lucide-react'

const menuConfig = [
  { to: '/connection', key: 'connection', icon: Database },
  { to: '/topology', key: 'topology', icon: Share2 },
  { to: '/vertex-type', key: 'vertexType', icon: CircleDot },
  { to: '/edge-type', key: 'edgeType', icon: Minus },
  { to: '/property-management', key: 'propertyManagement', icon: Tags },
  { to: '/vertex-data', key: 'vertexData', icon: Table },
  { to: '/edge-data', key: 'edgeData', icon: GitFork },
  { to: '/graph-explore', key: 'graphExplore', icon: Network },
  { to: '/gremlin', key: 'gremlin', icon: TerminalSquare },
  { to: '/import-export', key: 'importExport', icon: ArrowLeftRight },
  {
    groupKey: 'userPermission',
    icon: Users,
    children: [
      { to: '/user-management', key: 'userManagement', icon: User },
      { to: '/role-management', key: 'roleManagement', icon: ShieldCheck },
      { to: '/permission-overview', key: 'permissionOverview', icon: KeyRound },
    ],
  },
  {
    groupKey: 'auditLog',
    icon: ClipboardList,
    children: [
      { to: '/login-log', key: 'loginLog', icon: LogIn },
      { to: '/operation-log', key: 'operationLog', icon: ScrollText },
    ],
  },
]

const childActive = (children, pathname) =>
  children.some((c) => pathname === c.to || pathname.startsWith(c.to + '/'))

export default function Layout({ children }) {
  const { t } = useTranslation('layout')
  const { user, logout, hasMenu } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(() => new Set())
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [profileModal, setProfileModal] = useState(false)
  const [passwordModal, setPasswordModal] = useState(false)
  const userMenuRef = useRef(null)

  const menuToKey = (to) => to.replace(/^\//, '')

  const visibleItems = menuConfig.filter((item) => {
    if (item.groupKey) {
      const visibleChildren = item.children.filter((c) => hasMenu(menuToKey(c.to)))
      return visibleChildren.length > 0
    }
    return hasMenu(menuToKey(item.to))
  })

  const toggleGroup = (group) => {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(group)) next.delete(group)
      else next.add(group)
      return next
    })
  }

  const isExpanded = (item) =>
    childActive(item.children, location.pathname) || !collapsed.has(item.groupKey)

  useEffect(() => {
    const handler = (e) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) setUserMenuOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleLogout = async () => {
    setUserMenuOpen(false)
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-full">
      <aside className="flex w-60 flex-shrink-0 flex-col bg-slate-800 text-slate-300">
        <div className="flex h-14 items-center gap-2 border-b border-slate-700 px-4">
          <Boxes size={22} className="text-indigo-400" />
          <span className="text-base font-semibold text-white">{t('common:appName')}</span>
        </div>

        <nav className="flex-1 overflow-y-auto py-2">
          {visibleItems.map((item) => {
            if (item.groupKey) {
              const visibleChildren = item.children.filter((c) => hasMenu(menuToKey(c.to)))
              const expanded = isExpanded(item)
              const active = childActive(visibleChildren, location.pathname)
              const GroupIcon = item.icon
              return (
                <div key={item.groupKey}>
                  <button
                    type="button"
                    onClick={() => toggleGroup(item.groupKey)}
                    className={`flex w-full items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                      active
                        ? 'text-white'
                        : 'hover:bg-slate-700 hover:text-white'
                    }`}
                  >
                    <GroupIcon size={18} className={active ? 'text-indigo-400' : ''} />
                    <span className="flex-1 text-left">{t(`group.${item.groupKey}`)}</span>
                    {expanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                  </button>
                  {expanded &&
                    visibleChildren.map(({ to, key, icon: Icon }) => (
                      <NavLink
                        key={to}
                        to={to}
                        className={({ isActive }) =>
                          `flex items-center gap-3 py-2 pl-10 pr-4 text-sm transition-colors ${
                            isActive
                              ? 'bg-indigo-600 text-white'
                              : 'hover:bg-slate-700 hover:text-white'
                          }`
                        }
                      >
                        <Icon size={16} />
                        <span>{t(`menu.${key}`)}</span>
                      </NavLink>
                    ))}
                </div>
              )
            }

            const { to, key, icon: Icon } = item
            return (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                    isActive
                      ? 'bg-indigo-600 text-white'
                      : 'hover:bg-slate-700 hover:text-white'
                  }`
                }
              >
                <Icon size={18} />
                <span>{t(`menu.${key}`)}</span>
              </NavLink>
            )
          })}
        </nav>

        <div className="relative border-t border-slate-700 p-3" ref={userMenuRef}>
          <button
            onClick={() => setUserMenuOpen((v) => !v)}
            className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-600 text-xs font-medium text-white">
              {(user?.nickname || user?.username || '?').charAt(0).toUpperCase()}
            </div>
            <span className="flex-1 truncate text-left">
              {user?.nickname || user?.username || 'admin'}
            </span>
            <ChevronDown size={15} className={`transition-transform ${userMenuOpen ? 'rotate-180' : ''}`} />
          </button>

          {userMenuOpen && (
            <div className="absolute bottom-full left-3 right-3 mb-1 rounded-lg border border-slate-600 bg-slate-800 py-1 shadow-2xl">
              <div className="flex items-center gap-2 px-3 py-2 text-sm text-slate-300">
                <Globe size={15} className="text-slate-400" />
                <span className="flex-1">{t('user.language')}</span>
                <LanguageSwitcher variant="dark" />
              </div>
              <div className="my-1 border-t border-slate-700" />
              <button
                onClick={() => { setUserMenuOpen(false); setProfileModal(true) }}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
              >
                <User size={15} /> {t('user.profile')}
              </button>
              <button
                onClick={() => { setUserMenuOpen(false); setPasswordModal(true) }}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
              >
                <KeyRound size={15} /> {t('user.changePassword')}
              </button>
              <div className="my-1 border-t border-slate-700" />
              <button
                onClick={handleLogout}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-red-600 hover:text-white"
              >
                <LogOut size={15} /> {t('user.logout')}
              </button>
            </div>
          )}
        </div>
      </aside>

      <main className="flex-1 overflow-auto bg-gray-50">{children}</main>

      {profileModal && (
        <ProfileModal user={user} onClose={() => setProfileModal(false)} />
      )}
      {passwordModal && (
        <PasswordModal onClose={() => setPasswordModal(false)} />
      )}
    </div>
  )
}

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'

function ProfileModal({ user, onClose }) {
  const { t } = useTranslation('layout')
  const [form, setForm] = useState({
    nickname: user?.nickname || '',
    email: user?.email || '',
    phone: user?.phone || '',
    remark: user?.remark || '',
  })
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await updateProfile(form)
      showToast('success', t('profile.saveSuccess'))
      setTimeout(onClose, 800)
    } catch (err) {
      showToast('error', err.message)
    } finally {
      setSaving(false)
    }
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <form onSubmit={submit} className="w-full max-w-md overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">{t('profile.title')}</h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="max-h-[70vh] space-y-4 px-6 py-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('profile.username')}</label>
            <input className={`${inputCls} bg-gray-50`} value={user?.username || ''} disabled />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('profile.nickname')}</label>
            <input className={inputCls} value={form.nickname}
              onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('profile.email')}</label>
            <input className={inputCls} value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('profile.phone')}</label>
            <input className={inputCls} value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('profile.remark')}</label>
            <textarea className={inputCls} rows={2} value={form.remark}
              onChange={(e) => setForm({ ...form, remark: e.target.value })} />
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            {t('common:cancel')}
          </button>
          <button type="submit" disabled={saving}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Save size={15} /> {saving ? t('common:saving') : t('common:save')}
          </button>
        </div>
      </form>
      {toast && createPortal(
        <div className={`fixed bottom-6 right-6 z-[60] rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>,
        document.body
      )}
    </div>,
    document.body
  )
}

function PasswordModal({ onClose }) {
  const { t } = useTranslation('layout')
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const submit = async (e) => {
    e.preventDefault()
    if (!form.currentPassword) { showToast('error', t('password.requiredCurrent')); return }
    if (!form.newPassword || form.newPassword.length < 6) { showToast('error', t('password.minLength')); return }
    if (form.newPassword !== form.confirmPassword) { showToast('error', t('password.mismatch')); return }
    setSaving(true)
    try {
      await changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      showToast('success', t('password.success'))
      setTimeout(onClose, 800)
    } catch (err) {
      showToast('error', err.message)
    } finally {
      setSaving(false)
    }
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <form onSubmit={submit} className="w-full max-w-md overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">{t('password.title')}</h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="space-y-4 px-6 py-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('password.currentPassword')} <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
              placeholder={t('password.currentPlaceholder')} autoFocus />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('password.newPassword')} <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
              placeholder={t('password.newPlaceholder')} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">{t('password.confirmPassword')} <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
              placeholder={t('password.confirmPlaceholder')} />
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            {t('common:cancel')}
          </button>
          <button type="submit" disabled={saving}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Save size={15} /> {saving ? t('common:saving') : t('password.confirmButton')}
          </button>
        </div>
      </form>
      {toast && createPortal(
        <div className={`fixed bottom-6 right-6 z-[60] rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>,
        document.body
      )}
    </div>,
    document.body
  )
}
