import { useState, useEffect, useCallback, Fragment } from 'react'
import { createPortal } from 'react-dom'
import {
  searchOverviewUsers, getUserPermissionOverview,
  searchOverviewRoles, getRolePermissionOverview,
  lookupPermission,
} from '../api/permissionOverview'
import {
  KeyRound, Search, ShieldCheck, LayoutGrid, List, GitFork,
  TerminalSquare, AlertTriangle, AlertOctagon, Info, Users, X,
} from 'lucide-react'

const USER_TABS = [
  { key: 'summary', label: '权限摘要', icon: LayoutGrid },
  { key: 'menus', label: '菜单权限', icon: List },
  { key: 'operations', label: '操作权限', icon: ShieldCheck },
  { key: 'connections', label: '可见连接', icon: GitFork },
  { key: 'gremlin', label: 'Gremlin 权限', icon: TerminalSquare },
  { key: 'dangerous', label: '危险操作资格', icon: AlertOctagon },
  { key: 'checks', label: '配置检查', icon: AlertTriangle },
]

const ROLE_TABS = [
  { key: 'summary', label: '权限摘要', icon: LayoutGrid },
  { key: 'members', label: '用户成员', icon: Users },
  { key: 'menus', label: '菜单权限', icon: List },
  { key: 'operations', label: '操作权限', icon: ShieldCheck },
  { key: 'connections', label: '可见连接', icon: GitFork },
  { key: 'gremlin', label: 'Gremlin 权限', icon: TerminalSquare },
  { key: 'dangerous', label: '危险操作资格', icon: AlertOctagon },
  { key: 'checks', label: '配置检查', icon: AlertTriangle },
]

export default function PermissionOverview() {
  const [mode, setMode] = useState('users')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [list, setList] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [overview, setOverview] = useState(null)
  const [activeTab, setActiveTab] = useState('summary')
  const [loadingList, setLoadingList] = useState(false)
  const [loadingOverview, setLoadingOverview] = useState(false)
  const [lookupQuery, setLookupQuery] = useState(null)

  const openLookup = (type, code, label) => {
    setLookupQuery({ type, code, label })
  }

  const loadList = useCallback(async () => {
    setLoadingList(true)
    try {
      const params = {}
      if (keyword) params.keyword = keyword
      if (statusFilter !== '') params.status = Number(statusFilter)
      const res = mode === 'users'
        ? await searchOverviewUsers(params)
        : await searchOverviewRoles(params)
      setList(res.data?.rows || [])
    } catch { /* ignore */ } finally {
      setLoadingList(false)
    }
  }, [keyword, statusFilter, mode])

  useEffect(() => { loadList() }, [loadList])

  useEffect(() => {
    if (!selectedId && list.length > 0) {
      setSelectedId(list[0].id)
    }
  }, [list, selectedId])

  useEffect(() => {
    if (!selectedId) { setOverview(null); return }
    setLoadingOverview(true)
    const fetcher = mode === 'users'
      ? getUserPermissionOverview(selectedId)
      : getRolePermissionOverview(selectedId)
    fetcher
      .then((res) => setOverview(res.data))
      .catch(() => setOverview(null))
      .finally(() => setLoadingOverview(false))
  }, [selectedId, mode])

  const switchMode = (newMode) => {
    if (newMode === mode) return
    setMode(newMode)
    setSelectedId(null)
    setOverview(null)
    setActiveTab('summary')
    setKeyword('')
    setStatusFilter('')
  }

  const tabs = mode === 'users' ? USER_TABS : ROLE_TABS
  const placeholderText = mode === 'users' ? '搜索用户名/昵称' : '搜索角色编码/名称'
  const emptyText = mode === 'users' ? '暂无用户' : '暂无角色'
  const loadingText = mode === 'users' ? '加载权限数据...' : '加载角色权限...'

  return (
    <div className="flex h-full">
      {/* Left: search + list */}
      <aside className="flex w-72 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="border-b border-gray-200 px-4 py-3">
          <div className="flex rounded-lg bg-gray-100 p-0.5">
            <button
              onClick={() => switchMode('users')}
              className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-1.5 text-sm font-medium transition-colors ${
                mode === 'users' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              <Users size={14} /> 按用户
            </button>
            <button
              onClick={() => switchMode('roles')}
              className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-1.5 text-sm font-medium transition-colors ${
                mode === 'roles' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              <ShieldCheck size={14} /> 按角色
            </button>
          </div>
          <div className="relative mt-3">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && loadList()}
              placeholder={placeholderText}
              className="w-full rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="mt-2 w-full rounded-md border border-gray-300 px-2 py-1.5 text-sm outline-none focus:border-indigo-500"
          >
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loadingList && list.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-gray-400">加载中...</div>
          )}
          {!loadingList && list.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-gray-400">{emptyText}</div>
          )}
          {mode === 'users'
            ? list.map((u) => (
              <UserListItem key={u.id} item={u} selected={selectedId === u.id} onSelect={() => setSelectedId(u.id)} />
            ))
            : list.map((r) => (
              <RoleListItem key={r.id} item={r} selected={selectedId === r.id} onSelect={() => setSelectedId(r.id)} />
            ))
          }
        </div>
      </aside>

      {/* Right: overview */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {!overview ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {loadingOverview ? loadingText : `请在左侧选择${mode === 'users' ? '用户' : '角色'}`}
          </div>
        ) : (
          <>
            {mode === 'users'
              ? <UserHeader overview={overview} />
              : <RoleHeader overview={overview} />
            }
            <nav className="flex overflow-x-auto border-b border-gray-200 bg-white px-4">
              {tabs.map((tab) => {
                const badge = tab.key === 'checks' && overview.configChecks?.length > 0
                  ? overview.configChecks.length : null
                return (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    className={`flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm transition-colors ${
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
              {activeTab === 'summary' && mode === 'users' && <UserSummaryTab overview={overview} />}
              {activeTab === 'summary' && mode === 'roles' && <RoleSummaryTab overview={overview} />}
              {activeTab === 'members' && mode === 'roles' && <MembersTab overview={overview} />}
              {activeTab === 'menus' && <MenusTab overview={overview} onItemClick={openLookup} />}
              {activeTab === 'operations' && <OperationsTab overview={overview} onItemClick={openLookup} />}
              {activeTab === 'connections' && <ConnectionsTab overview={overview} onItemClick={openLookup} />}
              {activeTab === 'gremlin' && <GremlinTab overview={overview} onItemClick={openLookup} />}
              {activeTab === 'dangerous' && <DangerousTab overview={overview} onItemClick={openLookup} />}
              {activeTab === 'checks' && <ChecksTab overview={overview} />}
            </div>
          </>
        )}
      </div>

      {lookupQuery && (
        <PermissionLookupModal query={lookupQuery} onClose={() => setLookupQuery(null)} />
      )}
    </div>
  )
}

// ==================== 左侧列表项 ====================

function UserListItem({ item: u, selected, onSelect }) {
  return (
    <button
      onClick={onSelect}
      className={`flex w-full items-center gap-2 border-l-2 px-4 py-2.5 text-left transition-colors ${
        selected ? 'border-indigo-600 bg-indigo-50' : 'border-transparent hover:bg-gray-50'
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
  )
}

function RoleListItem({ item: r, selected, onSelect }) {
  return (
    <button
      onClick={onSelect}
      className={`flex w-full items-center gap-2 border-l-2 px-4 py-2.5 text-left transition-colors ${
        selected ? 'border-indigo-600 bg-indigo-50' : 'border-transparent hover:bg-gray-50'
      }`}
    >
      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 text-xs font-medium text-indigo-600">
        <ShieldCheck size={16} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className="truncate text-sm font-medium text-gray-800">{r.roleName}</span>
          {r.isBuiltin && (
            <span className="rounded bg-blue-50 px-1 py-0.5 text-[10px] text-blue-600">内置</span>
          )}
          {r.status === 0 && (
            <span className="rounded bg-gray-100 px-1 py-0.5 text-[10px] text-gray-500">停用</span>
          )}
        </div>
        <div className="truncate text-xs text-gray-400">{r.roleKey}</div>
      </div>
      <span className="rounded-full bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600">
        {r.userCount}人
      </span>
    </button>
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

// ==================== 角色信息头 ====================

function RoleHeader({ overview }) {
  const r = overview.role
  return (
    <div className="border-b border-gray-200 bg-white px-6 py-4">
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-100 text-indigo-600">
          <ShieldCheck size={24} />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold text-gray-800">{r.roleName}</span>
            <span className="rounded bg-gray-100 px-2 py-0.5 font-mono text-xs text-gray-500">{r.roleKey}</span>
            {r.isBuiltin && (
              <span className="rounded bg-blue-50 px-2 py-0.5 text-xs text-blue-600">内置角色</span>
            )}
            {r.status === 1
              ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">启用</span>
              : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">停用</span>}
          </div>
          <div className="mt-1 flex items-center gap-3 text-sm text-gray-500">
            <span>用户数量: <strong className="text-gray-700">{r.userCount}</strong></span>
            {r.description && <span className="text-gray-400">· {r.description}</span>}
          </div>
        </div>
      </div>
    </div>
  )
}

// ==================== 用户权限摘要 ====================

function UserSummaryTab({ overview }) {
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

  return (
    <div className="mx-auto max-w-4xl">
      <SummaryCards cards={cards} />
      {overview.user?.isSuperAdmin && (
        <div className="mt-4 rounded-lg border border-purple-200 bg-purple-50 px-4 py-3 text-sm text-purple-700">
          <Info size={15} className="mr-1 inline" />
          超级管理员拥有全部权限,包括所有菜单、操作、连接、Gremlin 危险级别和全部危险操作资格。
        </div>
      )}
    </div>
  )
}

// ==================== 角色权限摘要 ====================

function RoleSummaryTab({ overview }) {
  const s = overview.summary
  const cards = [
    { label: '用户成员', value: s.userCount, color: 'indigo' },
    { label: '菜单权限', value: s.menuCount, color: 'blue' },
    { label: '操作权限', value: s.operationCount, color: 'cyan' },
    { label: '可见连接', value: s.visibleConnectionCount, color: 'teal' },
    { label: 'Gremlin 权限', value: s.gremlinLevelLabel || s.gremlinLevel, color: 'purple', isText: true },
    { label: '危险操作资格', value: s.dangerousCount, color: 'red' },
    { label: '配置警告', value: s.warningCount, color: s.warningCount > 0 ? 'amber' : 'gray' },
  ]

  return (
    <div className="mx-auto max-w-4xl">
      <SummaryCards cards={cards} />
      <div className="mt-4 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">
        <Info size={15} className="mr-1 inline" />
        角色级检查仅关注该角色自身配置是否完整,不考虑其他角色补充的权限。
        用户级检查则基于所有角色合并后的最终权限。
      </div>
    </div>
  )
}

function SummaryCards({ cards }) {
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
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
      {cards.map((c) => (
        <div key={c.label} className={`rounded-lg border p-4 ${colorMap[c.color]}`}>
          <div className="text-xs opacity-70">{c.label}</div>
          <div className="mt-1 text-2xl font-bold">{c.value}</div>
        </div>
      ))}
    </div>
  )
}

// ==================== 用户成员 (角色视图) ====================

function MembersTab({ overview }) {
  const members = overview.members || []

  return (
    <div className="mx-auto max-w-3xl">
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">用户名</th>
              <th className="px-4 py-2">显示名称</th>
              <th className="px-4 py-2 w-20">状态</th>
              <th className="px-4 py-2">其他角色</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {members.length === 0 && (
              <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">暂无成员</td></tr>
            )}
            {members.map((m) => (
              <tr key={m.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 font-medium text-gray-800">{m.username}</td>
                <td className="px-4 py-2 text-gray-600">{m.nickname || '—'}</td>
                <td className="px-4 py-2">
                  {m.status === 1
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">启用</span>
                    : <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500">停用</span>}
                </td>
                <td className="px-4 py-2">
                  {m.otherRoles?.length > 0 ? (
                    <div className="flex flex-wrap gap-1">
                      {m.otherRoles.map((label, i) => (
                        <span key={i} className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">
                          {label}
                        </span>
                      ))}
                    </div>
                  ) : <span className="text-xs text-gray-400">无</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== 菜单权限 ====================

function MenusTab({ overview, onItemClick }) {
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
                  <tr
                    className="cursor-pointer hover:bg-indigo-50"
                    onClick={() => onItemClick?.('menu', m.key, m.label)}
                  >
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

function OperationsTab({ overview, onItemClick }) {
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
                <tr
                  key={op.code}
                  className="cursor-pointer hover:bg-indigo-50"
                  onClick={() => onItemClick?.('operation', op.code, op.label)}
                >
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

function ConnectionsTab({ overview, onItemClick }) {
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
              <tr
                key={c.id}
                className="cursor-pointer hover:bg-indigo-50"
                onClick={() => onItemClick?.('connection', c.id, c.name)}
              >
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

function GremlinTab({ overview, onItemClick }) {
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
            <div
              key={cap.key}
              className="flex cursor-pointer items-center justify-between rounded-md border-t border-gray-100 pt-2 transition-colors hover:bg-indigo-50"
              onClick={() => onItemClick?.('gremlin', cap.key, cap.label)}
            >
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
    </div>
  )
}

// ==================== 危险操作资格 ====================

function DangerousTab({ overview, onItemClick }) {
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
              <tr
                key={d.code}
                className="cursor-pointer hover:bg-indigo-50"
                onClick={() => onItemClick?.('dangerous', d.code, d.label)}
              >
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

// ==================== 权限反查弹窗 ====================

const TYPE_LABELS = {
  menu: '菜单权限',
  operation: '操作权限',
  connection: '可见连接',
  gremlin: 'Gremlin 权限',
  dangerous: '危险操作资格',
}

function PermissionLookupModal({ query, onClose }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    lookupPermission(query.type, query.code)
      .then((res) => setData(res.data))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [query.type, query.code])

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="flex max-h-[85vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="flex items-center gap-2 text-base font-semibold text-gray-800">
            <Search size={18} className="text-indigo-500" /> 权限反查
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-auto px-6 py-4">
          {loading && <div className="py-12 text-center text-sm text-gray-400">加载中...</div>}
          {error && <div className="py-12 text-center text-sm text-red-500">{error}</div>}
          {!loading && !error && data && <LookupContent data={data} />}
        </div>
      </div>
    </div>,
    document.body,
  )
}

function LookupContent({ data }) {
  const q = data.query
  const roles = data.roles || []
  const users = data.users || []
  const da = data.dangerousAnalysis

  return (
    <div className="space-y-5">
      <div className="rounded-lg bg-gray-50 px-4 py-3">
        <div className="grid grid-cols-3 gap-2 text-sm">
          <div>
            <span className="text-gray-400">权限类型</span>
            <div className="font-medium text-gray-700">{TYPE_LABELS[q.type] || q.type}</div>
          </div>
          <div>
            <span className="text-gray-400">权限编码</span>
            <div className="font-mono text-xs text-gray-700">{q.code}</div>
          </div>
          <div>
            <span className="text-gray-400">权限名称</span>
            <div className="font-medium text-gray-700">{q.label}</div>
          </div>
        </div>
      </div>

      <div>
        <h3 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-gray-700">
          <ShieldCheck size={15} className="text-indigo-500" />
          拥有该权限的角色
          <span className="rounded-full bg-indigo-100 px-1.5 text-xs text-indigo-600">{roles.length}</span>
        </h3>
        {roles.length === 0 ? (
          <div className="rounded-lg border border-gray-200 px-4 py-3 text-center text-sm text-gray-400">
            暂无角色拥有此权限
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {roles.map((r) => (
              <span key={r.key} className="flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
                <ShieldCheck size={14} className="text-indigo-400" />
                <span className="text-gray-700">{r.label}</span>
                {r.isBuiltin && <span className="rounded bg-blue-50 px-1 text-[10px] text-blue-500">内置</span>}
                {r.status === 0 && <span className="rounded bg-gray-100 px-1 text-[10px] text-gray-400">停用</span>}
              </span>
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-gray-700">
          <Users size={15} className="text-indigo-500" />
          最终拥有该权限的用户
          <span className="rounded-full bg-indigo-100 px-1.5 text-xs text-indigo-600">{users.length}</span>
        </h3>
        {users.length === 0 ? (
          <div className="rounded-lg border border-gray-200 px-4 py-3 text-center text-sm text-gray-400">
            暂无用户拥有此权限
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {users.map((u) => (
              <span key={u.id} className="flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
                <span className="text-gray-700">{u.username}</span>
                {u.nickname && <span className="text-xs text-gray-400">({u.nickname})</span>}
                {u.isSuperAdmin && <span className="rounded bg-purple-50 px-1 text-[10px] text-purple-500">超管</span>}
                {!u.isSuperAdmin && u.roleLabels?.length > 0 && (
                  <span className="text-xs text-gray-400">· {u.roleLabels.join('、')}</span>
                )}
              </span>
            ))}
          </div>
        )}
      </div>

      {da && <DangerousAnalysisPanel analysis={da} />}
    </div>
  )
}

function DangerousAnalysisPanel({ analysis: da }) {
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
      <h3 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-amber-800">
        <AlertOctagon size={15} />
        危险操作分析
      </h3>
      <div className="space-y-2 text-sm text-amber-800">
        <div>
          附加危险资格: <strong>{da.qualificationLabel}</strong>{' '}
          <span className="font-mono text-xs">({da.qualificationCode})</span>
        </div>
        {da.relatedOperations?.length > 0 && (
          <div>
            关联基础操作:{' '}
            {da.relatedOperations.map((op, i) => (
              <span key={op.code}>
                {i > 0 && '、'}
                <strong>{op.label}</strong>{' '}
                <span className="font-mono text-xs">({op.code})</span>
              </span>
            ))}
          </div>
        )}
        <div className="mt-3 grid grid-cols-3 gap-2 border-t border-amber-200 pt-3">
          <div className="text-center">
            <div className="text-2xl font-bold text-amber-700">{da.usersWithBaseOpCount}</div>
            <div className="text-xs text-amber-600">拥有基础操作权限</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-amber-700">{da.usersWithQualificationCount}</div>
            <div className="text-xs text-amber-600">同时拥有危险资格</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-red-600">{da.usersWhoCanExecuteCount}</div>
            <div className="text-xs text-red-500">最终可执行</div>
          </div>
        </div>
        {da.usersWhoCanExecute?.length > 0 && (
          <div className="mt-2 border-t border-amber-200 pt-2">
            <span className="text-xs text-amber-600">可执行用户: </span>
            <span className="text-xs text-amber-800">
              {da.usersWhoCanExecute.map((u) => u.username).join('、')}
            </span>
          </div>
        )}
      </div>
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
