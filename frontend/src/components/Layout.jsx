import { NavLink, useNavigate } from 'react-router-dom'
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
  { to: '/operation-log', label: '操作日志', icon: ScrollText },
]

export default function Layout({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

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
          {menuItems.map(({ to, label, icon: Icon }) => (
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
          ))}
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
