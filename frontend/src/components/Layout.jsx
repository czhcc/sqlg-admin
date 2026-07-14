import { useState } from 'react'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
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

  const handleLogout = async () => {
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

        <div className="border-t border-slate-700 p-3">
          <div className="mb-2 truncate px-1 text-xs text-slate-400">
            {user?.nickname || user?.username || 'admin'}
          </div>
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-300 transition-colors hover:bg-red-600 hover:text-white"
          >
            <LogOut size={16} />
            退出登录
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto bg-gray-50">{children}</main>
    </div>
  )
}
