import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import {
  getUserDetail, createUser, updateUser, assignRoles, resetPassword,
  getRoles, getPermissions, getConnectionPermissions,
  getUserLoginLogs, getUserOperationLogs,
} from '../api/userManagement'
import {
  ArrowLeft, Users, ShieldCheck, GitFork, LogIn, ScrollText, Save, X,
  Check, KeyRound,
} from 'lucide-react'

const TABS = [
  { key: 'basic', label: '基本信息', icon: Users },
  { key: 'roles', label: '角色分配', icon: ShieldCheck },
  { key: 'permissions', label: '有效权限', icon: ShieldCheck },
  { key: 'connections', label: '连接权限', icon: GitFork },
  { key: 'login-logs', label: '登录记录', icon: LogIn },
  { key: 'operation-logs', label: '操作记录', icon: ScrollText },
]

export default function UserDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isNew = id === 'new'
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') || 'basic')
  const [detail, setDetail] = useState(null)
  const [roles, setRoles] = useState([])
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const loadDetail = useCallback(async () => {
    if (isNew) {
      setDetail({ username: '', nickname: '', email: '', phone: '', remark: '', status: 1, roles: [] })
      return
    }
    try {
      const res = await getUserDetail(id)
      setDetail(res.data)
    } catch (e) { showToast('error', e.message) }
  }, [id, isNew])

  const loadRoles = useCallback(async () => {
    try {
      const res = await getRoles()
      setRoles(res.data || [])
    } catch (e) { showToast('error', e.message) }
  }, [])

  useEffect(() => { loadDetail(); loadRoles() }, [loadDetail, loadRoles])

  if (!detail) return <div className="flex h-full items-center justify-center text-gray-400">加载中...</div>

  const readOnlyForNew = isNew && !['basic', 'roles'].includes(activeTab)

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-gray-200 bg-white px-6 py-3">
        <button onClick={() => navigate('/user-management')}
          className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
          <ArrowLeft size={18} /> 返回
        </button>
        <div className="h-4 w-px bg-gray-200" />
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          {isNew ? '新增用户' : `用户详情: ${detail.username}`}
        </h1>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <nav className="flex w-48 flex-col border-r border-gray-200 bg-white py-2">
          {TABS.map((tab) => {
            const disabled = isNew && !['basic', 'roles'].includes(tab.key)
            return (
              <button
                key={tab.key}
                onClick={() => !disabled && setActiveTab(tab.key)}
                disabled={disabled}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm transition-colors ${
                  activeTab === tab.key
                    ? 'border-r-2 border-indigo-600 bg-indigo-50 font-medium text-indigo-700'
                    : disabled
                      ? 'cursor-not-allowed text-gray-300'
                      : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                <tab.icon size={16} /> {tab.label}
              </button>
            )
          })}
        </nav>

        <div className="flex-1 overflow-auto p-6">
          {activeTab === 'basic' && (
            <BasicTab detail={detail} isNew={isNew} userId={id} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />
          )}
          {activeTab === 'roles' && !readOnlyForNew && (
            <RolesTab detail={detail} roles={roles} userId={id} isNew={isNew} onSaved={() => { loadDetail(); showToast('success', '保存成功') }} showToast={showToast} />
          )}
          {activeTab === 'permissions' && !isNew && <PermissionsTab userId={id} />}
          {activeTab === 'connections' && !isNew && <ConnectionsTab userId={id} />}
          {activeTab === 'login-logs' && !isNew && <LoginLogsTab userId={id} />}
          {activeTab === 'operation-logs' && !isNew && <OperationLogsTab userId={id} />}
          {readOnlyForNew && (
            <div className="flex h-full items-center justify-center text-sm text-gray-400">
              保存用户后可查看此标签页
            </div>
          )}
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

// ==================== 基本信息 ====================

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'

function BasicTab({ detail, isNew, userId, onSaved, showToast }) {
  const [form, setForm] = useState({
    username: detail.username || '',
    password: '',
    nickname: detail.nickname || '',
    email: detail.email || '',
    phone: detail.phone || '',
    remark: detail.remark || '',
    status: detail.status ?? 1,
  })
  const [saving, setSaving] = useState(false)

  const submit = async () => {
    if (isNew) {
      if (!form.username || !form.password) { showToast('error', '用户名和密码必填'); return }
    }
    setSaving(true)
    try {
      if (isNew) {
        const res = await createUser(form)
        onSaved()
        window.history.replaceState(null, '', `/user-detail/${res.data.id}`)
      } else {
        await updateUser(userId, {
          nickname: form.nickname, email: form.email,
          phone: form.phone, remark: form.remark, status: form.status,
        })
        onSaved()
      }
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="grid grid-cols-2 gap-4">
        {isNew && (
          <>
            <Field label="用户名" required>
              <input className={inputCls} value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })} />
            </Field>
            <Field label="密码" required>
              <input className={inputCls} type="password" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </Field>
          </>
        )}
        {!isNew && (
          <Field label="用户名">
            <input className={`${inputCls} bg-gray-50`} value={detail.username} disabled />
          </Field>
        )}
        <Field label="显示名称">
          <input className={inputCls} value={form.nickname}
            onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
        </Field>
        <Field label="邮箱">
          <input className={inputCls} value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="手机号">
          <input className={inputCls} value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </Field>
        <Field label="状态">
          <select className={inputCls} value={form.status}
            onChange={(e) => setForm({ ...form, status: Number(e.target.value) })}>
            <option value={1}>启用</option>
            <option value={0}>停用</option>
          </select>
        </Field>
        <div className="col-span-2">
          <Field label="备注">
            <textarea className={inputCls} rows={3} value={form.remark}
              onChange={(e) => setForm({ ...form, remark: e.target.value })} />
          </Field>
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2">
        <button onClick={() => window.history.back()}
          className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
          取消
        </button>
        <button onClick={submit} disabled={saving}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          <Save size={15} /> {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  )
}

// ==================== 角色分配 ====================

function RolesTab({ detail, roles, userId, isNew, onSaved, showToast }) {
  const [selectedRoles, setSelectedRoles] = useState(
    isNew ? [] : (detail.roles || []).map((r) => r.key)
  )
  const [newPassword, setNewPassword] = useState('')
  const [saving, setSaving] = useState(false)

  const toggleRole = (key) => {
    setSelectedRoles((prev) => prev.includes(key) ? prev.filter((r) => r !== key) : [...prev, key])
  }

  const saveRoles = async () => {
    if (isNew) {
      showToast('info', '请先在「基本信息」保存用户')
      return
    }
    setSaving(true)
    try {
      await assignRoles(userId, selectedRoles)
      onSaved()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const doResetPassword = async () => {
    if (!newPassword || newPassword.length < 6) { showToast('error', '密码至少 6 位'); return }
    try {
      await resetPassword(userId, newPassword)
      setNewPassword('')
      showToast('success', '密码已重置')
    } catch (e) { showToast('error', e.message) }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <div>
        <h3 className="mb-3 text-sm font-semibold text-gray-700">分配角色</h3>
        <div className="space-y-2">
          {roles.map((role) => {
            const key = role.roleKey || role.key
            const label = role.roleName || role.label
            return (
            <label key={key}
              className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors ${
                selectedRoles.includes(key)
                  ? 'border-indigo-300 bg-indigo-50'
                  : 'border-gray-200 hover:bg-gray-50'
              }`}>
              <input type="checkbox" checked={selectedRoles.includes(key)}
                onChange={() => toggleRole(key)}
                className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
              <div>
                <div className="text-sm font-medium text-gray-800">{label}</div>
                <div className="text-xs text-gray-500">{role.description}</div>
              </div>
            </label>
            )
          })}
        </div>
        <div className="mt-4 flex justify-end">
          <button onClick={saveRoles} disabled={saving || isNew}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Save size={15} /> 保存角色
          </button>
        </div>
      </div>

      {!isNew && (
        <div className="border-t border-gray-200 pt-6">
          <h3 className="mb-3 text-sm font-semibold text-gray-700">重置密码</h3>
          <div className="flex items-center gap-2">
            <input className={inputCls} type="password" value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)} placeholder="输入新密码 (至少 6 位)" />
            <button onClick={doResetPassword}
              className="flex items-center gap-1 whitespace-nowrap rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-700 hover:bg-amber-100">
              <KeyRound size={15} /> 重置
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 有效权限 (只读) ====================

function PermissionsTab({ userId }) {
  const [perms, setPerms] = useState(null)

  useEffect(() => {
    getPermissions(userId).then((res) => setPerms(res.data)).catch(() => {})
  }, [userId])

  if (!perms) return <div className="text-center text-gray-400">加载中...</div>

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PermSection title="当前角色">
        {perms.roles?.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {perms.roles.map((r) => (
              <span key={r.key} className="rounded bg-indigo-50 px-2 py-0.5 text-sm text-indigo-700">{r.label}</span>
            ))}
          </div>
        ) : <span className="text-sm text-gray-400">未分配角色</span>}
      </PermSection>

      <PermSection title="菜单权限">
        <div className="flex flex-wrap gap-1">
          {perms.menus?.length > 0 ? perms.menus.map((m) => (
            <span key={m} className="rounded bg-green-50 px-2 py-0.5 text-sm text-green-700">{m === '*' ? '全部菜单' : m}</span>
          )) : <span className="text-sm text-gray-400">无</span>}
        </div>
      </PermSection>

      <PermSection title="操作权限">
        <div className="flex flex-wrap gap-1">
          {perms.operations?.length > 0 ? perms.operations.map((op) => (
            <span key={op} className="rounded bg-blue-50 px-2 py-0.5 text-sm text-blue-700">{op === '*' ? '全部操作' : op}</span>
          )) : <span className="text-sm text-gray-400">无</span>}
        </div>
      </PermSection>

      <PermSection title="Gremlin 权限">
        <span className={`rounded px-2 py-0.5 text-sm ${
          perms.gremlin === 'ADMIN' ? 'bg-purple-50 text-purple-700' :
          perms.gremlin === 'READ_WRITE' ? 'bg-blue-50 text-blue-700' :
          perms.gremlin === 'READ_ONLY' ? 'bg-cyan-50 text-cyan-700' :
          'bg-gray-100 text-gray-500'
        }`}>{perms.gremlin || 'NONE'}</span>
      </PermSection>

      <PermSection title="危险操作资格">
        {perms.allowDangerousOps
          ? <span className="rounded bg-red-50 px-2 py-0.5 text-sm text-red-700">允许</span>
          : <span className="rounded bg-gray-100 px-2 py-0.5 text-sm text-gray-500">无</span>}
      </PermSection>
    </div>
  )
}

// ==================== 连接权限 (只读) ====================

function ConnectionsTab({ userId }) {
  const [data, setData] = useState(null)

  useEffect(() => {
    getConnectionPermissions(userId).then((res) => setData(res.data)).catch(() => {})
  }, [userId])

  if (!data) return <div className="text-center text-gray-400">加载中...</div>

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 rounded-lg bg-indigo-50 px-4 py-3 text-sm text-indigo-700">
        可访问连接数量: <strong>{data.accessibleCount ?? 0}</strong> · 合并访问级别: <strong>{data.mergedAccess || 'NONE'}</strong>
      </div>
      <div className="overflow-hidden rounded-lg border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">连接范围</th>
              <th className="px-4 py-2">访问级别</th>
              <th className="px-4 py-2">来源角色</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data.permissions?.length > 0 ? data.permissions.map((p, i) => (
              <tr key={i} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-gray-700">{p.scope}</td>
                <td className="px-4 py-2">
                  <span className={`rounded px-2 py-0.5 text-xs ${
                    p.access === 'READ_WRITE' ? 'bg-green-50 text-green-700' : 'bg-cyan-50 text-cyan-700'
                  }`}>{p.access}</span>
                </td>
                <td className="px-4 py-2 text-gray-600">{p.roleLabel}</td>
              </tr>
            )) : (
              <tr><td colSpan={3} className="px-4 py-8 text-center text-gray-400">未分配角色</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== 登录记录 ====================

function LoginLogsTab({ userId }) {
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(10)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getUserLoginLogs(userId, { page, size })
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) { /* ignore */ }
    finally { setLoading(false) }
  }, [userId, page, size])

  useEffect(() => { load() }, [load])

  const totalPages = Math.ceil(total / size) || 1

  return (
    <div>
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">登录时间</th>
              <th className="px-4 py-2">IP</th>
              <th className="px-4 py-2">结果</th>
              <th className="px-4 py-2">User-Agent</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">加载中...</td></tr>}
            {!loading && rows.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">暂无记录</td></tr>}
            {!loading && rows.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-xs text-gray-600 whitespace-nowrap">{formatTime(r.loginTime)}</td>
                <td className="px-4 py-2 text-xs text-gray-600">{r.clientIp || '—'}</td>
                <td className="px-4 py-2">
                  {r.resultStatus === 'SUCCESS'
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">成功</span>
                    : <span className="rounded bg-red-50 px-1.5 py-0.5 text-xs text-red-700">失败 {r.failReason ? `(${r.failReason})` : ''}</span>}
                </td>
                <td className="max-w-xs truncate px-4 py-2 text-xs text-gray-500" title={r.userAgent}>{r.userAgent || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {total > size && (
        <div className="mt-2 flex items-center justify-between text-xs text-gray-500">
          <span>共 {total} 条</span>
          <div className="flex gap-1">
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">上一页</button>
            <span className="px-1">{page}/{totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">下一页</button>
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 操作记录 ====================

function OperationLogsTab({ userId }) {
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(10)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getUserOperationLogs(userId, { page, size })
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) { /* ignore */ }
    finally { setLoading(false) }
  }, [userId, page, size])

  useEffect(() => { load() }, [load])

  const totalPages = Math.ceil(total / size) || 1

  return (
    <div>
      <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">时间</th>
              <th className="px-4 py-2">模块</th>
              <th className="px-4 py-2">操作</th>
              <th className="px-4 py-2">状态</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">加载中...</td></tr>}
            {!loading && rows.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">暂无记录</td></tr>}
            {!loading && rows.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-xs text-gray-600 whitespace-nowrap">{formatTime(r.createTime)}</td>
                <td className="px-4 py-2 text-xs text-gray-700">{r.module || '—'}</td>
                <td className="px-4 py-2 text-xs text-gray-700">
                  <div className="flex items-center gap-1">
                    {r.isDangerous && <span className="text-red-500">⚠</span>}
                    <span className="truncate max-w-[240px]" title={r.operationName || r.action}>{r.operationName || r.action || '—'}</span>
                  </div>
                </td>
                <td className="px-4 py-2">
                  <span className={`rounded px-1.5 py-0.5 text-xs ${
                    r.status === 'SUCCESS' ? 'bg-green-50 text-green-700' :
                    r.status === 'FAILED' ? 'bg-red-50 text-red-700' :
                    'bg-amber-50 text-amber-700'
                  }`}>{r.status || '—'}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {total > size && (
        <div className="mt-2 flex items-center justify-between text-xs text-gray-500">
          <span>共 {total} 条</span>
          <div className="flex gap-1">
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">上一页</button>
            <span className="px-1">{page}/{totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">下一页</button>
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 共享组件 ====================

function Field({ label, required, children }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      {children}
    </div>
  )
}

function PermSection({ title, children }) {
  return (
    <div className="rounded-lg border border-gray-200 p-4">
      <h3 className="mb-2 text-sm font-semibold text-gray-700">{title}</h3>
      {children}
    </div>
  )
}

function formatTime(time) {
  if (!time) return '—'
  const d = new Date(time)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
