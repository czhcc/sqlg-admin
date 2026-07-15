import { useState, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { updateProfile, changePassword } from '../api/auth'
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
} from 'lucide-react'

const menuItems = [
  { to: '/connection', label: '连接管理', icon: Database },
  { to: '/topology', label: 'Topology 浏览', icon: Share2 },
  { to: '/vertex-type', label: '点类型管理', icon: CircleDot },
  { to: '/edge-type', label: '边类型管理', icon: Minus },
  { to: '/property-management', label: '属性管理', icon: Tags },
  { to: '/vertex-data', label: '点数据管理', icon: Table },
  { to: '/edge-data', label: '边数据管理', icon: GitFork },
  { to: '/graph-explore', label: '图关系展开', icon: Network },
  { to: '/gremlin', label: 'Gremlin 控制台', icon: TerminalSquare },
  { to: '/import-export', label: '导入导出', icon: ArrowLeftRight },
  {
    group: '用户与权限',
    icon: Users,
    children: [
      { to: '/user-management', label: '用户管理', icon: User },
      { to: '/role-management', label: '角色管理', icon: ShieldCheck },
      { to: '/permission-overview', label: '权限总览', icon: KeyRound },
    ],
  },
  {
    group: '审计日志',
    icon: ClipboardList,
    children: [
      { to: '/login-log', label: '登录日志', icon: LogIn },
      { to: '/operation-log', label: '操作日志', icon: ScrollText },
    ],
  },
]

const childActive = (children, pathname) =>
  children.some((c) => pathname === c.to || pathname.startsWith(c.to + '/'))

export default function Layout({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(() => new Set())
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [profileModal, setProfileModal] = useState(false)
  const [passwordModal, setPasswordModal] = useState(false)
  const userMenuRef = useRef(null)

  const toggleGroup = (group) => {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(group)) next.delete(group)
      else next.add(group)
      return next
    })
  }

  // 包含当前路由的分组始终展开,其余按用户折叠状态
  const isExpanded = (item) =>
    childActive(item.children, location.pathname) || !collapsed.has(item.group)

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
          <span className="text-base font-semibold text-white">图数据库管理平台</span>
        </div>

        <nav className="flex-1 overflow-y-auto py-2">
          {menuItems.map((item) => {
            if (item.group) {
              const expanded = isExpanded(item)
              const active = childActive(item.children, location.pathname)
              const GroupIcon = item.icon
              return (
                <div key={item.group}>
                  <button
                    type="button"
                    onClick={() => toggleGroup(item.group)}
                    className={`flex w-full items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                      active
                        ? 'text-white'
                        : 'hover:bg-slate-700 hover:text-white'
                    }`}
                  >
                    <GroupIcon size={18} className={active ? 'text-indigo-400' : ''} />
                    <span className="flex-1 text-left">{item.group}</span>
                    {expanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                  </button>
                  {expanded &&
                    item.children.map(({ to, label, icon: Icon }) => (
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
                        <span>{label}</span>
                      </NavLink>
                    ))}
                </div>
              )
            }

            const { to, label, icon: Icon } = item
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
                <span>{label}</span>
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
              <button
                onClick={() => { setUserMenuOpen(false); setProfileModal(true) }}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
              >
                <User size={15} /> 个人信息
              </button>
              <button
                onClick={() => { setUserMenuOpen(false); setPasswordModal(true) }}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-slate-700 hover:text-white"
              >
                <KeyRound size={15} /> 修改密码
              </button>
              <div className="my-1 border-t border-slate-700" />
              <button
                onClick={handleLogout}
                className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-red-600 hover:text-white"
              >
                <LogOut size={15} /> 退出登录
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
      showToast('success', '保存成功')
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
          <h2 className="text-base font-semibold text-gray-800">个人信息</h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="max-h-[70vh] space-y-4 px-6 py-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">用户名</label>
            <input className={`${inputCls} bg-gray-50`} value={user?.username || ''} disabled />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">显示名称</label>
            <input className={inputCls} value={form.nickname}
              onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">邮箱</label>
            <input className={inputCls} value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">手机号</label>
            <input className={inputCls} value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">备注</label>
            <textarea className={inputCls} rows={2} value={form.remark}
              onChange={(e) => setForm({ ...form, remark: e.target.value })} />
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            取消
          </button>
          <button type="submit" disabled={saving}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Save size={15} /> {saving ? '保存中...' : '保存'}
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
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const submit = async (e) => {
    e.preventDefault()
    if (!form.currentPassword) { showToast('error', '请输入当前密码'); return }
    if (!form.newPassword || form.newPassword.length < 6) { showToast('error', '新密码至少 6 位'); return }
    if (form.newPassword !== form.confirmPassword) { showToast('error', '两次输入的新密码不一致'); return }
    setSaving(true)
    try {
      await changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      showToast('success', '密码修改成功')
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
          <h2 className="text-base font-semibold text-gray-800">修改密码</h2>
          <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="space-y-4 px-6 py-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">当前密码 <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
              placeholder="请输入当前密码" autoFocus />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">新密码 <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
              placeholder="至少 6 位" />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">确认新密码 <span className="text-red-500">*</span></label>
            <input className={inputCls} type="password" value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
              placeholder="再次输入新密码" />
          </div>
        </div>
        <div className="flex justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button type="button" onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            取消
          </button>
          <button type="submit" disabled={saving}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Save size={15} /> {saving ? '保存中...' : '确认修改'}
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
