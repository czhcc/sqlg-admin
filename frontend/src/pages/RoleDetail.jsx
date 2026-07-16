import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import {
  getRoleDetail, getRoleCatalog, updateRoleBasic,
  updateMenuPermissions, updateOperationPermissions,
  updateGremlinPermission, updateDangerousPermissions,
  getConnectionAuth, updateConnectionAuth,
  getRoleMembers, addRoleMembers, removeRoleMember,
} from '../api/roleManagement'
import { pageUsers } from '../api/userManagement'
import {
  ArrowLeft, Settings, Users, List, ShieldCheck, GitFork,
  TerminalSquare, AlertTriangle, Save, Check, X, Plus, Trash2,
  ChevronDown, ChevronRight,
} from 'lucide-react'

const TABS = [
  { key: 'basic', label: '基本信息', icon: Settings },
  { key: 'members', label: '用户成员', icon: Users },
  { key: 'menus', label: '菜单权限', icon: List },
  { key: 'operations', label: '操作权限', icon: ShieldCheck },
  { key: 'connections', label: '可见连接', icon: GitFork },
  { key: 'gremlin', label: 'Gremlin 权限', icon: TerminalSquare },
  { key: 'dangerous', label: '危险操作资格', icon: AlertTriangle },
]

export default function RoleDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') || 'basic')
  const [detail, setDetail] = useState(null)
  const [catalog, setCatalog] = useState(null)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const loadDetail = useCallback(async () => {
    try { const res = await getRoleDetail(id); setDetail(res.data) }
    catch (e) { showToast('error', e.message) }
  }, [id])

  const loadCatalog = useCallback(async () => {
    try { const res = await getRoleCatalog(); setCatalog(res.data) }
    catch (e) { showToast('error', e.message) }
  }, [])

  useEffect(() => { loadDetail(); loadCatalog() }, [loadDetail, loadCatalog])

  if (!detail) return <div className="flex h-full items-center justify-center text-gray-400">加载中...</div>

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-gray-200 bg-white px-6 py-3">
        <button onClick={() => navigate('/role-management')}
          className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
          <ArrowLeft size={18} /> 返回
        </button>
        <div className="h-4 w-px bg-gray-200" />
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          角色详情: {detail.roleName}
          {detail.isBuiltin && <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">内置</span>}
        </h1>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <nav className="flex w-48 flex-col border-r border-gray-200 bg-white py-2">
          {TABS.map((tab) => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm transition-colors ${
                activeTab === tab.key
                  ? 'border-r-2 border-indigo-600 bg-indigo-50 font-medium text-indigo-700'
                  : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <tab.icon size={16} /> {tab.label}
            </button>
          ))}
        </nav>

        <div className="flex-1 overflow-auto p-6">
          {activeTab === 'basic' && <BasicTab detail={detail} roleId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />}
          {activeTab === 'members' && <MembersTab roleId={id} roleKey={detail.roleKey} />}
          {activeTab === 'menus' && catalog && <MenuPermissionTab detail={detail} catalog={catalog} roleId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />}
          {activeTab === 'operations' && catalog && <OperationPermissionTab detail={detail} catalog={catalog} roleId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />}
          {activeTab === 'connections' && <ConnectionAuthTab roleId={id} />}
          {activeTab === 'gremlin' && catalog && <GremlinTab detail={detail} catalog={catalog} roleId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />}
          {activeTab === 'dangerous' && catalog && <DangerousTab detail={detail} catalog={catalog} roleId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />}
        </div>
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>
      )}
    </div>
  )
}

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'

// ==================== 基本信息 ====================

function BasicTab({ detail, roleId, onSaved, showToast }) {
  const [form, setForm] = useState({
    roleName: detail.roleName || '',
    description: detail.description || '',
    status: detail.status ?? 1,
  })
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    setSaving(true)
    try {
      await updateRoleBasic(roleId, form)
      onSaved()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700">角色编码</label>
        <input className={`${inputCls} bg-gray-50 font-mono`} value={detail.roleKey} disabled />
        <p className="mt-1 text-xs text-gray-400">角色编码不可修改</p>
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700">角色名称</label>
        <input className={inputCls} value={form.roleName}
          onChange={(e) => setForm({ ...form, roleName: e.target.value })} />
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700">角色说明</label>
        <textarea className={inputCls} rows={3} value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700">状态</label>
        <select className={inputCls} value={form.status}
          onChange={(e) => setForm({ ...form, status: Number(e.target.value) })}>
          <option value={1}>启用</option>
          <option value={0}>停用</option>
        </select>
      </div>
      <div className="flex justify-end">
        <button onClick={submit} disabled={saving}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          <Save size={15} /> {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  )
}

// ==================== 用户成员 ====================

function MembersTab({ roleId, roleKey }) {
  const [memberIds, setMemberIds] = useState([])
  const [allUsers, setAllUsers] = useState([])
  const [loading, setLoading] = useState(false)
  const [showAdd, setShowAdd] = useState(false)
  const [selectedNew, setSelectedNew] = useState([])
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [memberRes, userRes] = await Promise.all([
        getRoleMembers(roleId),
        pageUsers({ page: 1, size: 200 }),
      ])
      setMemberIds(memberRes.data?.userIds || [])
      setAllUsers(userRes.data?.rows || [])
    } catch (e) { showToast('error', e.message) }
    finally { setLoading(false) }
  }, [roleId])

  useEffect(() => { load() }, [load])

  const members = allUsers.filter((u) => memberIds.includes(u.id))
  const candidates = allUsers.filter((u) => !memberIds.includes(u.id))

  const onRemove = async (userId) => {
    try {
      await removeRoleMember(roleId, userId)
      showToast('success', '已移除')
      load()
    } catch (e) { showToast('error', e.message) }
  }

  const onAdd = async () => {
    if (selectedNew.length === 0) { showToast('error', '请选择用户'); return }
    try {
      await addRoleMembers(roleId, selectedNew)
      setSelectedNew([])
      setShowAdd(false)
      showToast('success', `已添加 ${selectedNew.length} 个用户`)
      load()
    } catch (e) { showToast('error', e.message) }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <span className="text-sm text-gray-600">共 {members.length} 个用户</span>
        <button onClick={() => setShowAdd(!showAdd)}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700">
          <Plus size={15} /> 添加用户
        </button>
      </div>

      {showAdd && (
        <div className="mb-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4">
          <div className="mb-2 text-sm font-medium text-gray-700">选择要添加的用户</div>
          <div className="max-h-48 space-y-1 overflow-auto">
            {candidates.length === 0 && <p className="text-sm text-gray-400">没有可添加的用户</p>}
            {candidates.map((u) => (
              <label key={u.id} className="flex items-center gap-2 rounded px-2 py-1 hover:bg-white">
                <input type="checkbox" checked={selectedNew.includes(u.id)}
                  onChange={() => setSelectedNew((prev) => prev.includes(u.id) ? prev.filter((x) => x !== u.id) : [...prev, u.id])}
                  className="h-4 w-4 rounded border-gray-300 text-indigo-600" />
                <span className="text-sm text-gray-700">{u.username} ({u.nickname || '—'})</span>
              </label>
            ))}
          </div>
          <div className="mt-3 flex justify-end gap-2">
            <button onClick={() => { setShowAdd(false); setSelectedNew([]) }}
              className="rounded border border-gray-300 bg-white px-3 py-1 text-xs text-gray-600 hover:bg-gray-50">取消</button>
            <button onClick={onAdd}
              className="rounded bg-indigo-600 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-700">确认添加</button>
          </div>
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">用户名</th>
              <th className="px-4 py-2">显示名称</th>
              <th className="px-4 py-2">状态</th>
              <th className="px-4 py-2 w-10"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">加载中...</td></tr>}
            {!loading && members.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">暂无成员</td></tr>}
            {!loading && members.map((m) => (
              <tr key={m.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 font-medium text-gray-800">{m.username}</td>
                <td className="px-4 py-2 text-gray-600">{m.nickname || '—'}</td>
                <td className="px-4 py-2">
                  {m.status === 1
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">启用</span>
                    : <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500">停用</span>}
                </td>
                <td className="px-4 py-2">
                  <button onClick={() => onRemove(m.id)} title="移除"
                    className="flex h-7 w-7 items-center justify-center rounded text-red-400 hover:bg-red-50 hover:text-red-600">
                    <Trash2 size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>
      )}
    </div>
  )
}

// ==================== 菜单权限 ====================

function MenuPermissionTab({ detail, catalog, roleId, onSaved, showToast }) {
  const [selected, setSelected] = useState(new Set(detail.menuPermissions || []))
  const [saving, setSaving] = useState(false)

  const allMenuKeys = []
  const flattenMenus = (items) => {
    items.forEach((item) => {
      if (item.children) flattenMenus(item.children)
      else allMenuKeys.push(item.key)
    })
  }
  flattenMenus(catalog.menus)

  const toggle = (key) => {
    setSelected((prev) => { const n = new Set(prev); n.has(key) ? n.delete(key) : n.add(key); return n })
  }

  const save = async () => {
    setSaving(true)
    try {
      await updateMenuPermissions(roleId, [...selected])
      onSaved()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const renderMenuTree = (items, depth = 0) => items.map((item) => {
    if (item.children) {
      const allChildChecked = item.children.every((c) => selected.has(c.key))
      const someChildChecked = item.children.some((c) => selected.has(c.key))
      return (
        <div key={item.key} style={{ marginLeft: depth * 20 }}>
          <label className="flex items-center gap-2 py-1.5">
            <input type="checkbox" checked={allChildChecked} ref={el => el && (el.indeterminate = someChildChecked && !allChildChecked)}
              onChange={() => item.children.forEach((c) => { if (allChildChecked) selected.delete(c.key); else selected.add(c.key); setSelected(new Set(selected)) })}
              className="h-4 w-4 rounded border-gray-300 text-indigo-600 font-semibold" />
            <span className="text-sm font-semibold text-gray-700">{item.label}</span>
          </label>
          <div>{renderMenuTree(item.children, depth + 1)}</div>
        </div>
      )
    }
    return (
      <label key={item.key} className="flex items-center gap-2 py-1" style={{ marginLeft: depth * 20 }}>
        <input type="checkbox" checked={selected.has(item.key)}
          onChange={() => toggle(item.key)}
          className="h-4 w-4 rounded border-gray-300 text-indigo-600" />
        <span className="text-sm text-gray-600">{item.label}</span>
      </label>
    )
  })

  return (
    <div>
      <div className="mb-4">{renderMenuTree(catalog.menus)}</div>
      <button onClick={save} disabled={saving}
        className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
        <Save size={15} /> {saving ? '保存中...' : '保存'}
      </button>
    </div>
  )
}

// ==================== 操作权限 ====================

function OperationPermissionTab({ detail, catalog, roleId, onSaved, showToast }) {
  const [selected, setSelected] = useState(new Set(detail.operationPermissions || []))
  const [saving, setSaving] = useState(false)
  const [expanded, setExpanded] = useState({})

  const toggle = (code) => {
    setSelected((prev) => { const n = new Set(prev); n.has(code) ? n.delete(code) : n.add(code); return n })
  }

  const toggleMenuAll = (menu) => {
    const codes = menu.operations.map((o) => o.code)
    const allChecked = codes.every((c) => selected.has(c))
    setSelected((prev) => {
      const n = new Set(prev)
      codes.forEach((c) => allChecked ? n.delete(c) : n.add(c))
      return n
    })
  }

  const save = async () => {
    setSaving(true)
    try {
      await updateOperationPermissions(roleId, [...selected])
      onSaved()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const renderOpTree = (items, depth = 0) => items.map((item) => {
    if (item.children) {
      const childChecked = item.children.flatMap((c) => c.operations || []).filter((o) => selected.has(o.code))
      const childTotal = item.children.flatMap((c) => c.operations || []).length
      return (
        <div key={item.key} className="mb-3" style={{ marginLeft: depth * 0 }}>
          <div className="flex items-center gap-2 border-b border-gray-200 bg-gray-100 px-3 py-2 text-sm font-bold text-gray-700">
            <span className="text-gray-400">{item.label}</span>
            <span className="text-xs text-gray-400 font-normal">({childChecked.length}/{childTotal})</span>
          </div>
          <div className="border-l-2 border-gray-100">
            {renderOpTree(item.children, depth + 1)}
          </div>
        </div>
      )
    }
    const allChecked = item.operations.every((o) => selected.has(o.code))
    const someChecked = item.operations.some((o) => selected.has(o.code))
    const isOpen = expanded[item.key] !== false
    const indent = 20 + depth * 24
    return (
      <div key={item.key} className="mb-1.5">
        <div className="flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 shadow-sm hover:border-indigo-200"
          style={{ marginLeft: indent }}>
          <input type="checkbox" checked={allChecked} ref={el => el && (el.indeterminate = someChecked && !allChecked)}
            onChange={() => toggleMenuAll(item)}
            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
          <button onClick={() => setExpanded({ ...expanded, [item.key]: !isOpen })}
            className="flex flex-1 items-center gap-1.5 text-left">
            {isOpen
              ? <ChevronDown size={14} className="flex-shrink-0 text-gray-400" />
              : <ChevronRight size={14} className="flex-shrink-0 text-gray-400" />}
            <span className="text-sm font-semibold text-gray-700">{item.label}</span>
            <span className="rounded-full bg-gray-100 px-1.5 py-0.5 text-[10px] font-normal text-gray-400">
              {item.operations.filter((o) => selected.has(o.code)).length}/{item.operations.length}
            </span>
          </button>
        </div>
        {isOpen && (
          <div className="mt-0.5 space-y-0.5 border-l border-gray-200" style={{ marginLeft: indent + 16 }}>
            {item.operations.map((op) => (
              <label key={op.code}
                className={`flex cursor-pointer items-center gap-2 rounded px-3 py-1.5 text-sm transition-colors ${
                  selected.has(op.code) ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-50'
                }`}>
                <input type="checkbox" checked={selected.has(op.code)}
                  onChange={() => toggle(op.code)}
                  className="h-3.5 w-3.5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
                <span>{op.label}</span>
                <span className="rounded bg-gray-100 px-1 py-0.5 font-mono text-[10px] text-gray-400">{op.code}</span>
              </label>
            ))}
          </div>
        )}
      </div>
    )
  })

  return (
    <div>
      <div className="mb-4">{renderOpTree(catalog.menus)}</div>
      <button onClick={save} disabled={saving}
        className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
        <Save size={15} /> {saving ? '保存中...' : '保存'}
      </button>
    </div>
  )
}

// ==================== 可见连接 ====================

function ConnectionAuthTab({ roleId }) {
  const [data, setData] = useState(null)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    try { const res = await getConnectionAuth(roleId); setData(res.data) }
    catch (e) { showToast('error', e.message) }
  }, [roleId])

  useEffect(() => { load() }, [load])

  if (!data) return <div className="text-center text-gray-400">加载中...</div>

  const onToggle = async (connId, visible) => {
    try { await updateConnectionAuth(roleId, connId, visible); load() }
    catch (e) { showToast('error', e.message) }
  }

  const visibleCount = data.connections?.filter((c) => c.visible).length || 0
  const totalCount = data.connections?.length || 0

  return (
    <div>
      <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
        <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">
          {visibleCount} / {totalCount} 个连接可见
        </span>
        <span className="text-gray-400">勾选的连接将对此角色的用户显示</span>
      </div>

      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">连接名称</th>
              <th className="px-4 py-2">类型</th>
              <th className="px-4 py-2">状态</th>
              <th className="px-4 py-2 text-center">可见</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data.connections?.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">暂无连接</td></tr>}
            {data.connections?.map((c) => (
              <tr key={c.connectionId} className="hover:bg-gray-50">
                <td className="px-4 py-2 font-medium text-gray-800">{c.connectionName}</td>
                <td className="px-4 py-2 text-xs text-gray-500">{c.dbType}</td>
                <td className="px-4 py-2">
                  {c.status === 1
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">启用</span>
                    : <span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500">停用</span>}
                </td>
                <td className="px-4 py-2 text-center">
                  <button
                    onClick={() => onToggle(c.connectionId, !c.visible)}
                    className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                      c.visible ? 'bg-indigo-600' : 'bg-gray-300'
                    }`}
                  >
                    <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow transition-transform ${
                      c.visible ? 'translate-x-4' : 'translate-x-1'
                    }`} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>
      )}
    </div>
  )
}

// ==================== Gremlin 权限 ====================

function GremlinTab({ detail, catalog, roleId, onSaved, showToast }) {
  const [level, setLevel] = useState(detail.gremlinPermission || 'READ_ONLY')
  const [saving, setSaving] = useState(false)

  const save = async () => {
    setSaving(true)
    try { await updateGremlinPermission(roleId, level); onSaved() }
    catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const order = ['READ_ONLY', 'WRITE', 'DANGEROUS']
  const sorted = [...catalog.gremlinLevels].sort((a, b) => order.indexOf(a.value) - order.indexOf(b.value))

  return (
    <div className="mx-auto max-w-xl">
      <div className="space-y-2">
        {sorted.map((lv) => (
          <label key={lv.value} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors ${
            level === lv.value ? 'border-indigo-300 bg-indigo-50' : 'border-gray-200 hover:bg-gray-50'
          }`}>
            <input type="radio" name="gremlin" checked={level === lv.value}
              onChange={() => setLevel(lv.value)}
              className="mt-0.5 h-4 w-4 border-gray-300 text-indigo-600" />
            <div>
              <div className="text-sm font-medium text-gray-800">{lv.label}</div>
              <div className="mt-1 text-xs text-gray-500">{lv.description}</div>
            </div>
          </label>
        ))}
      </div>
      <div className="mt-4">
        <button onClick={save} disabled={saving}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          <Save size={15} /> {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  )
}

// ==================== 危险操作资格 ====================

function DangerousTab({ detail, catalog, roleId, onSaved, showToast }) {
  const [selected, setSelected] = useState(new Set(detail.dangerousPermissions || []))
  const [saving, setSaving] = useState(false)

  const toggle = (code) => {
    setSelected((prev) => { const n = new Set(prev); n.has(code) ? n.delete(code) : n.add(code); return n })
  }

  const save = async () => {
    setSaving(true)
    try { await updateDangerousPermissions(roleId, [...selected]); onSaved() }
    catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const operationPerms = new Set(detail.operationPermissions || [])
  const dangerousOps = catalog.dangerousOps || []

  const warnings = []
  for (const dq of dangerousOps) {
    const hasQualification = selected.has(dq.code)
    const matchingOps = (dq.operations || []).filter((op) => operationPerms.has(op))
    if (hasQualification && matchingOps.length === 0) {
      warnings.push({
        type: ' qualification-without-ops',
        message: `已配置「${dq.label}」危险操作资格,但该角色当前没有相关操作权限,此资格暂时不会生效。`,
      })
    }
    if (!hasQualification && matchingOps.length > 0) {
      const opLabels = matchingOps.join('、')
      warnings.push({
        type: 'ops-without-qualification',
        message: `已配置 ${opLabels} 操作权限,但未配置「${dq.label}」危险操作资格,该角色仍无法执行这些操作。`,
      })
    }
  }

  return (
    <div>
      <div className="mb-4 space-y-3">
        {dangerousOps.map((dq) => {
          const checked = selected.has(dq.code)
          return (
            <label key={dq.code} className={`flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors ${
              checked ? 'border-amber-300 bg-amber-50' : 'border-gray-200 hover:bg-gray-50'
            }`}>
              <input type="checkbox" checked={checked}
                onChange={() => toggle(dq.code)}
                className="mt-0.5 h-4 w-4 rounded border-gray-300 text-amber-600 focus:ring-amber-500" />
              <div>
                <div className="text-sm font-medium text-gray-800">{dq.label}</div>
                <div className="mt-1 text-xs text-gray-500">{dq.description}</div>
              </div>
            </label>
          )
        })}
      </div>

      {warnings.length > 0 && (
        <div className="mb-4 space-y-2">
          {warnings.map((w, i) => (
            <div key={i} className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-xs ${
              w.type === 'qualification-without-ops'
                ? 'border-blue-200 bg-blue-50 text-blue-700'
                : 'border-amber-200 bg-amber-50 text-amber-700'
            }`}>
              <AlertTriangle size={14} className="mt-0.5 flex-shrink-0" />
              <span>{w.message}</span>
            </div>
          ))}
        </div>
      )}

      <button onClick={save} disabled={saving}
        className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
        <Save size={15} /> {saving ? '保存中...' : '保存'}
      </button>
    </div>
  )
}
