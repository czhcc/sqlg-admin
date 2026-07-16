import { useState, useEffect, useCallback, Fragment } from 'react'
import { searchOverviewUsers, getUserPermissionOverview } from '../api/permissionOverview'
import {
  KeyRound, Search, ShieldCheck, LayoutGrid, List, GitFork,
  TerminalSquare, AlertTriangle, AlertOctagon, Info,
} from 'lucide-react'

const TABS = [
  { key: 'summary', label: '权限摘要', icon: LayoutGrid },
  { key: 'menus', label: '菜单权限', icon: List },
  { key: 'operations', label: '操作权限', icon: ShieldCheck },
  { key: 'connections', label: '可见连接', icon: GitFork },
  { key: 'gremlin', label: 'Gremlin 权限', icon: TerminalSquare },
  { key: 'dangerous', label: '危险操作资格', icon: AlertOctagon },
  { key: 'checks', label: '配置检查', icon: AlertTriangle },
]

export default function PermissionOverview() {
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [users, setUsers] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [overview, setOverview] = useState(null)
  const [activeTab, setActiveTab] = useState('summary')
  const [loadingUsers, setLoadingUsers] = useState(false)
  const [loadingOverview, setLoadingOverview] = useState(false)

  const loadUsers = useCallback(async () => {
    setLoadingUsers(true)
    try {
      const params = {}
      if (keyword) params.keyword = keyword
      if (statusFilter !== '') params.status = Number(statusFilter)
      const res = await searchOverviewUsers(params)
      setUsers(res.data?.rows || [])
    } catch { /* ignore */ } finally {
      setLoadingUsers(false)
    }
  }, [keyword, statusFilter])

  useEffect(() => { loadUsers() }, [loadUsers])

  useEffect(() => {
    if (!selectedId && users.length > 0) {
      setSelectedId(users[0].id)
    }
  }, [users, selectedId])

  useEffect(() => {
    if (!selectedId) { setOverview(null); return }
    setLoadingOverview(true)
    getUserPermissionOverview(selectedId)
      .then((res) => setOverview(res.data))
      .catch(() => setOverview(null))
      .finally(() => setLoadingOverview(false))
  }, [selectedId])

  return (
    <div className="flex h-full">
      {/* Left: user search + list */}
      <aside className="flex w-72 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="border-b border-gray-200 px-4 py-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-gray-700">
            <KeyRound size={16} className="text-indigo-500" /> 选择用户
          </h2>
          <div className="relative mt-2">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && loadUsers()}
              placeholder="搜索用户名/昵称"
              className="w-full rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value) }}
            className="mt-2 w-full rounded-md border border-gray-300 px-2 py-1.5 text-sm outline-none focus:border-indigo-500"
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loadingUsers && users.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-gray-400">加载中...</div>
          )}
          {!loadingUsers && users.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-gray-400">暂无用户</div>
          )}
          {users.map((u) => (
            <button
              key={u.id}
              onClick={() => setSelectedId(u.id)}
              className={`flex w-full items-center gap-2 border-l-2 px-4 py-2.5 text-left transition-colors ${
                selectedId === u.id
                  ? 'border-indigo-600 bg-indigo-50'
                  : 'border-transparent hover:bg-gray-50'
              }`}
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-xs font-medium text-slate-600">
                {(u.nickname || u.username || '?').charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="truncate text-sm font-medium text-gray-800">
                    {u.nickname || u.username}
                  </span>
                  {u.isSuperAdmin && (
                    <span className="rounded bg-purple-50 px-1 py-0.5 text-[10px] text-purple-600">超管</span>
                  )}
                  {u.status === 0 && (
                    <span className="rounded bg-gray-100 px-1 py-0.5 text-[10px] text-gray-500">停用</span>
                  )}
                </div>
                <div className="truncate text-xs text-gray-400">{u.username}</div>
              </div>
              {u.roleCount > 0 && (
                <span className="rounded-full bg-indigo-50 px-1.5 py-0.5 text-[10px] text-indigo-600">
                  {u.roleCount}角色
                </span>
              )}
            </button>
          ))}
        </div>
      </aside>

      {/* Right: overview */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {!overview ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {loadingOverview ? '加载权限数据...' : '请在左侧选择用户'}
          </div>
        ) : (
          <>
            <UserHeader overview={overview} />
            <nav className="flex border-b border-gray-200 bg-white px-4">
              {TABS.map((tab) => {
                const badge = tab.key === 'checks' && overview.configChecks?.length > 0
                  ? overview.configChecks.length : null
                return (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    className={`flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm transition-colors ${
                      activeTab === tab.key
                        ? 'border-indigo-600 font-medium text-indigo-700'
                        : 'border-transparent text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    <tab.icon size={15} /> {tab.label}
                    {badge && (
                      <span className="rounded-full bg-amber-100 px-1.5 text-[10px] font-medium text-amber-700">
                        {badge}
                      </span>
                    )}
                  </button>
                )
              })}
            </nav>
            <div className="flex-1 overflow-auto bg-gray-50 p-6">
              {activeTab === 'summary' && <SummaryTab overview={overview} />}
              {activeTab === 'menus' && <MenusTab overview={overview} />}
              {activeTab === 'operations' && <OperationsTab overview={overview} />}
              {activeTab === 'connections' && <ConnectionsTab overview={overview} />}
              {activeTab === 'gremlin' && <GremlinTab overview={overview} />}
              {activeTab === 'dangerous' && <DangerousTab overview={overview} />}
              {activeTab === 'checks' && <ChecksTab overview={overview} />}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ==================== 用户信息头 ====================

function UserHeader({ overview }) {
  const u = overview.user
  return (
    <div className="border-b border-gray-200 bg-white px-6 py-4">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-100 text-lg font-semibold text-indigo-600">
          {(u.nickname || u.username || '?').charAt(0).toUpperCase()}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold text-gray-800">{u.username}</span>
            {u.nickname && <span className="text-sm text-gray-500">({u.nickname})</span>}
            {u.isSuperAdmin && (
              <span className="rounded bg-purple-50 px-2 py-0.5 text-xs text-purple-600">超级管理员</span>
            )}
            {u.status === 1
              ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">启用</span>
              : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">停用</span>}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5 text-sm text-gray-500">
            <span>所属角色:</span>
            {u.roles?.length > 0 ? u.roles.map((r) => (
              <span key={r.key} className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">
                {r.label}
              </span>
            )) : <span className="text-gray-400">未分配</span>}
          </div>
        </div>
      </div>
    </div>
  )
}

// ==================== 权限摘要 ====================

function SummaryTab({ overview }) {
  const s = overview.summary
  const cards = [
    { label: '所属角色', value: s.roleCount, color: 'indigo' },
    { label: '可访问菜单', value: s.menuCount, color: 'blue' },
    { label: '操作权限', value: s.operationCount, color: 'cyan' },
    { label: '可见连接', value: s.visibleConnectionCount, color: 'teal' },
    { label: 'Gremlin 权限', value: s.gremlinLevelLabel || s.gremlinLevel, color: 'purple', isText: true },
    { label: '危险操作资格', value: s.dangerousCount, color: 'red' },
    { label: '配置警告', value: s.warningCount, color: s.warningCount > 0 ? 'amber' : 'gray' },
  ]

  const colorMap = {
    indigo: 'border-indigo-200 bg-indigo-50 text-indigo-700',
    blue: 'border-blue-200 bg-blue-50 text-blue-700',
    cyan: 'border-cyan-200 bg-cyan-50 text-cyan-700',
    teal: 'border-teal-200 bg-teal-50 text-teal-700',
    purple: 'border-purple-200 bg-purple-50 text-purple-700',
    red: 'border-red-200 bg-red-50 text-red-700',
    amber: 'border-amber-200 bg-amber-50 text-amber-700',
    gray: 'border-gray-200 bg-gray-50 text-gray-600',
  }

  return (
    <div className="mx-auto max-w-4xl">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className={`rounded-lg border p-4 ${colorMap[c.color]}`}>
            <div className="text-xs opacity-70">{c.label}</div>
            <div className="mt-1 text-2xl font-bold">
              {c.isText ? c.value : c.value}
            </div>
          </div>
        ))}
      </div>
      {overview.user?.isSuperAdmin && (
        <div className="mt-4 rounded-lg border border-purple-200 bg-purple-50 px-4 py-3 text-sm text-purple-700">
          <Info size={15} className="mr-1 inline" />
          超级管理员拥有全部权限,包括所有菜单、操作、连接、Gremlin 危险级别和全部危险操作资格。
        </div>
      )}
    </div>
  )
}

// ==================== 菜单权限 ====================

function MenusTab({ overview }) {
  const menus = overview.menus || []
  let currentGroup = null

  return (
    <div className="mx-auto max-w-3xl">
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">菜单名称</th>
              <th className="px-4 py-2 w-24">状态</th>
              <th className="px-4 py-2">来源</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {menus.map((m) => {
              const showGroup = m.group && m.group !== currentGroup
              if (showGroup) currentGroup = m.group
              return (
                <Fragment key={m.key}>
                  {showGroup && (
                    <tr className="bg-gray-50">
                      <td colSpan={3} className="px-4 py-1.5 text-xs font-semibold uppercase text-gray-400">
                        {m.group}
                      </td>
                    </tr>
                  )}
                  <tr className="hover:bg-gray-50">
                    <td className="px-4 py-2 text-gray-700">{m.label}</td>
                    <td className="px-4 py-2"><StatusBadge granted={m.granted} /></td>
                    <td className="px-4 py-2">
                      <SourceBadges sources={m.sources} />
                    </td>
                  </tr>
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== 操作权限 ====================

function OperationsTab({ overview }) {
  const groups = overview.operations || []

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      {groups.map((g) => (
        <div key={g.menuKey} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <div className="border-b border-gray-200 bg-gray-50 px-4 py-2 text-sm font-semibold text-gray-700">
            {g.menuLabel}
          </div>
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <tbody className="divide-y divide-gray-100">
              {g.operations.map((op) => (
                <tr key={op.code} className="hover:bg-gray-50">
                  <td className="px-4 py-2 pl-8 text-gray-700">{op.label}</td>
                  <td className="px-4 py-2 w-24"><StatusBadge granted={op.granted} /></td>
                  <td className="px-4 py-2 text-xs">
                    <SourceBadges sources={op.sources} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  )
}

// ==================== 可见连接 ====================

function ConnectionsTab({ overview }) {
  const conns = overview.connections || []

  return (
    <div className="mx-auto max-w-3xl">
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">连接名称</th>
              <th className="px-4 py-2">类型</th>
              <th className="px-4 py-2 w-20">状态</th>
              <th className="px-4 py-2 w-20">可见</th>
              <th className="px-4 py-2">权限来源</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {conns.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">暂无连接</td></tr>
            )}
            {conns.map((c) => (
              <tr key={c.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 font-medium text-gray-800">{c.name}</td>
                <td className="px-4 py-2 text-gray-600">{c.dbType}</td>
                <td className="px-4 py-2">
                  {c.status === 1
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">启用</span>
                    : <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500">停用</span>}
                </td>
                <td className="px-4 py-2">
                  {c.visible
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">可见</span>
                    : <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-400">不可见</span>}
                </td>
                <td className="px-4 py-2">
                  <SourceBadges sources={c.sources} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== Gremlin 权限 ====================

function GremlinTab({ overview }) {
  const g = overview.gremlin
  if (!g) return null

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="rounded-lg border border-gray-200 bg-white p-4">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-700">最终 Gremlin 级别</h3>
          <span className={`rounded px-2 py-0.5 text-sm font-medium ${
            g.level === 'DANGEROUS' ? 'bg-red-50 text-red-700' :
            g.level === 'WRITE' ? 'bg-blue-50 text-blue-700' :
            g.level === 'READ_ONLY' ? 'bg-cyan-50 text-cyan-700' :
            'bg-gray-100 text-gray-500'
          }`}>{g.levelLabel || g.level}</span>
        </div>
        <div className="space-y-2">
          {g.capabilities?.map((cap) => (
            <div key={cap.key} className="flex items-center justify-between border-t border-gray-100 pt-2">
              <div>
                <span className="text-sm text-gray-700">{cap.label}</span>
                <span className="ml-2 text-xs text-gray-400">
                  {cap.sources?.length > 0 ? `来源: ${cap.sources.join('、')}` : ''}
                </span>
              </div>
              {cap.granted
                ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">允许</span>
                : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-400">不允许</span>}
            </div>
          ))}
        </div>
      </div>

      {g.details?.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h3 className="mb-3 text-sm font-semibold text-gray-700">各角色 Gremlin 级别</h3>
          <div className="space-y-2">
            {g.details.map((d) => (
              <div key={d.role} className="flex items-center justify-between">
                <span className="text-sm text-gray-700">{d.roleLabel}</span>
                <span className={`rounded px-2 py-0.5 text-xs ${
                  d.level === 'DANGEROUS' ? 'bg-red-50 text-red-700' :
                  d.level === 'WRITE' ? 'bg-blue-50 text-blue-700' :
                  d.level === 'READ_ONLY' ? 'bg-cyan-50 text-cyan-700' :
                  'bg-gray-100 text-gray-500'
                }`}>{d.levelLabel || d.level}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 危险操作资格 ====================

function DangerousTab({ overview }) {
  const items = overview.dangerous || []

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <Info size={15} className="mr-1 inline" />
        危险操作资格只是附加条件,不会单独授予具体操作能力。
        执行危险操作时,仍需拥有对应的基础操作权限。
      </div>
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">危险操作</th>
              <th className="px-4 py-2 w-24">状态</th>
              <th className="px-4 py-2">来源</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {items.map((d) => (
              <tr key={d.code} className="hover:bg-gray-50">
                <td className="px-4 py-2">
                  <div className="font-medium text-gray-800">{d.label}</div>
                  <div className="text-xs text-gray-500">{d.description}</div>
                </td>
                <td className="px-4 py-2">
                  {d.granted
                    ? <span className="rounded bg-red-50 px-2 py-0.5 text-xs text-red-700">已授权</span>
                    : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-400">未授权</span>}
                </td>
                <td className="px-4 py-2">
                  <SourceBadges sources={d.sources} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== 配置检查 ====================

function ChecksTab({ overview }) {
  const checks = overview.configChecks || []

  return (
    <div className="mx-auto max-w-2xl space-y-3">
      {checks.length === 0 ? (
        <div className="rounded-lg border border-green-200 bg-green-50 px-6 py-12 text-center">
          <ShieldCheck size={32} className="mx-auto mb-2 text-green-500" />
          <div className="text-sm font-medium text-green-700">配置检查通过</div>
          <div className="mt-1 text-xs text-green-600">未发现无效、矛盾或不完整的权限配置</div>
        </div>
      ) : (
        checks.map((c, i) => (
          <div key={i} className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
            <AlertTriangle size={18} className="mt-0.5 flex-shrink-0 text-amber-500" />
            <div className="flex-1">
              <div className="text-sm text-amber-800">{c.message}</div>
            </div>
          </div>
        ))
      )}
    </div>
  )
}

// ==================== 共享组件 ====================

function StatusBadge({ granted }) {
  return granted
    ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">已授权</span>
    : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-400">未授权</span>
}

function SourceBadges({ sources }) {
  if (!sources || sources.length === 0) {
    return <span className="text-xs text-gray-300">—</span>
  }
  return (
    <div className="flex flex-wrap gap-1">
      {sources.map((s, i) => (
        <span key={i} className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">{s}</span>
      ))}
    </div>
  )
}
