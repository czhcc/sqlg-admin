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
import { useTranslation } from 'react-i18next'

const TABS = [
  { key: 'basic', labelKey: 'tab.basic', icon: Users },
  { key: 'roles', labelKey: 'tab.roles', icon: ShieldCheck },
  { key: 'permissions', labelKey: 'tab.permissions', icon: ShieldCheck },
  { key: 'connections', labelKey: 'tab.connections', icon: GitFork },
  { key: 'login-logs', labelKey: 'tab.loginLogs', icon: LogIn },
  { key: 'operation-logs', labelKey: 'tab.operationLogs', icon: ScrollText },
]

export default function UserDetail() {
  const { t, i18n } = useTranslation('userDetail')
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

  if (!detail) return <div className="flex h-full items-center justify-center text-gray-400">{t('loading')}</div>

  const readOnlyForNew = isNew && !['basic', 'roles'].includes(activeTab)

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-gray-200 bg-white px-6 py-3">
        <button onClick={() => navigate('/user-management')}
          className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
          <ArrowLeft size={18} /> {t('back')}
        </button>
        <div className="h-4 w-px bg-gray-200" />
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          {isNew ? t('newUser') : t('detailTitle', { name: detail.username })}
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
                <tab.icon size={16} /> {t(tab.labelKey)}
              </button>
            )
          })}
        </nav>

        <div className="flex-1 overflow-auto p-6">
          {activeTab === 'basic' && (
            <BasicTab detail={detail} isNew={isNew} userId={id} onSaved={() => { loadDetail(); showToast('success', t('msg.saveSuccess')) }} showToast={showToast} />
          )}
          {activeTab === 'roles' && !readOnlyForNew && (
            <RolesTab detail={detail} roles={roles} userId={id} isNew={isNew} onSaved={() => { loadDetail(); showToast('success', t('msg.saveSuccess')) }} showToast={showToast} />
          )}
          {activeTab === 'permissions' && !isNew && <PermissionsTab userId={id} />}
          {activeTab === 'connections' && !isNew && <ConnectionsTab userId={id} />}
          {activeTab === 'login-logs' && !isNew && <LoginLogsTab userId={id} />}
          {activeTab === 'operation-logs' && !isNew && <OperationLogsTab userId={id} />}
          {readOnlyForNew && (
            <div className="flex h-full items-center justify-center text-sm text-gray-400">
              {t('saveFirstHint')}
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
  const { t } = useTranslation('userDetail')
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
      if (!form.username || !form.password) { showToast('error', t('msg.usernamePasswordRequired')); return }
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
            <Field label={t('field.username')} required>
              <input className={inputCls} value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })} />
            </Field>
            <Field label={t('field.password')} required>
              <input className={inputCls} type="password" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </Field>
          </>
        )}
        {!isNew && (
          <Field label={t('field.username')}>
            <input className={`${inputCls} bg-gray-50`} value={detail.username} disabled />
          </Field>
        )}
        <Field label={t('field.nickname')}>
          <input className={inputCls} value={form.nickname}
            onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
        </Field>
        <Field label={t('field.email')}>
          <input className={inputCls} value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label={t('field.phone')}>
          <input className={inputCls} value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </Field>
        <Field label={t('field.status')}>
          <select className={inputCls} value={form.status}
            onChange={(e) => setForm({ ...form, status: Number(e.target.value) })}>
            <option value={1}>{t('common:enable')}</option>
            <option value={0}>{t('common:disable')}</option>
          </select>
        </Field>
        <div className="col-span-2">
          <Field label={t('field.remark')}>
            <textarea className={inputCls} rows={3} value={form.remark}
              onChange={(e) => setForm({ ...form, remark: e.target.value })} />
          </Field>
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-2">
        <button onClick={() => window.history.back()}
          className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
          {t('cancel')}
        </button>
        <button onClick={submit} disabled={saving}
          className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          <Save size={15} /> {saving ? t('common:saving') : t('save')}
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
      showToast('info', t('roles.saveUserFirst'))
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
    if (!newPassword || newPassword.length < 6) { showToast('error', t('roles.passwordMinLength')); return }
    try {
      await resetPassword(userId, newPassword)
      setNewPassword('')
      showToast('success', t('roles.passwordReset'))
    } catch (e) { showToast('error', e.message) }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <div>
        <h3 className="mb-3 text-sm font-semibold text-gray-700">{t('roles.assignTitle')}</h3>
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
            <Save size={15} /> {t('roles.saveRoles')}
          </button>
        </div>
      </div>

      {!isNew && (
        <div className="border-t border-gray-200 pt-6">
          <h3 className="mb-3 text-sm font-semibold text-gray-700">{t('roles.resetPassword')}</h3>
          <div className="flex items-center gap-2">
            <input className={inputCls} type="password" value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)} placeholder={t("roles.newPasswordPlaceholder")} />
            <button onClick={doResetPassword}
              className="flex items-center gap-1 whitespace-nowrap rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-700 hover:bg-amber-100">
              <KeyRound size={15} /> {t('roles.resetBtn')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 有效权限 (只读) ====================

function PermissionsTab({ userId }) {
  const { t } = useTranslation('userDetail')
  const [perms, setPerms] = useState(null)

  useEffect(() => {
    getPermissions(userId).then((res) => setPerms(res.data)).catch(() => {})
  }, [userId])

  if (!perms) return <div className="text-center text-gray-400">{t('loading')}</div>

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PermSection title={t("perms.currentRoles")}>
        {perms.roles?.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {perms.roles.map((r) => (
              <span key={r.key} className="rounded bg-indigo-50 px-2 py-0.5 text-sm text-indigo-700">{r.label}</span>
            ))}
          </div>
        ) : <span className="text-sm text-gray-400">{t('perms.unassigned')}</span>}
      </PermSection>

      <PermSection title={t("perms.menuPerms")}>
        <div className="flex flex-wrap gap-1">
          {perms.menus?.length > 0 ? perms.menus.map((m) => (
            <span key={m} className="rounded bg-green-50 px-2 py-0.5 text-sm text-green-700">{m === '*' ? t('perms.allMenus') : m}</span>
          )) : <span className="text-sm text-gray-400">{t('none')}</span>}
        </div>
      </PermSection>

      <PermSection title={t("perms.operationPerms")}>
        <div className="flex flex-wrap gap-1">
          {perms.operations?.length > 0 ? perms.operations.map((op) => (
            <span key={op} className="rounded bg-blue-50 px-2 py-0.5 text-sm text-blue-700">{op === '*' ? t('perms.allOps') : op}</span>
          )) : <span className="text-sm text-gray-400">{t('none')}</span>}
        </div>
      </PermSection>

      <PermSection title={t("perms.gremlinPerms")}>
        <span className={`rounded px-2 py-0.5 text-sm ${
          perms.gremlin === 'ADMIN' ? 'bg-purple-50 text-purple-700' :
          perms.gremlin === 'READ_WRITE' ? 'bg-blue-50 text-blue-700' :
          perms.gremlin === 'READ_ONLY' ? 'bg-cyan-50 text-cyan-700' :
          'bg-gray-100 text-gray-500'
        }`}>{perms.gremlin || 'NONE'}</span>
      </PermSection>

      <PermSection title={t("perms.dangerousQuals")}>
        {perms.allowDangerousOps
          ? <span className="rounded bg-red-50 px-2 py-0.5 text-sm text-red-700">{t('perms.allowed')}</span>
          : <span className="rounded bg-gray-100 px-2 py-0.5 text-sm text-gray-500">{t('none')}</span>}
      </PermSection>
    </div>
  )
}

// ==================== 连接权限 (只读) ====================

function ConnectionsTab({ userId }) {
  const { t } = useTranslation('userDetail')
  const [data, setData] = useState(null)

  useEffect(() => {
    getConnectionPermissions(userId).then((res) => setData(res.data)).catch(() => {})
  }, [userId])

  if (!data) return <div className="text-center text-gray-400">{t('loading')}</div>

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 rounded-lg bg-indigo-50 px-4 py-3 text-sm text-indigo-700">
        {t('conn.accessibleCount')}: <strong>{data.accessibleCount ?? 0}</strong> · · {t('conn.mergedAccess')}: <strong>{data.mergedAccess || 'NONE'}</strong>
      </div>
      <div className="overflow-hidden rounded-lg border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
            <tr>
              <th className="px-4 py-2">{t('conn.colScope')}</th>
              <th className="px-4 py-2">{t('conn.colAccess')}</th>
              <th className="px-4 py-2">{t('conn.colSource')}</th>
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
              <tr><td colSpan={3} className="px-4 py-8 text-center text-gray-400">{t('perms.unassigned')}</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== 登录记录 ====================

function LoginLogsTab({ userId }) {
  const { t, i18n } = useTranslation('userDetail')
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
              <th className="px-4 py-2">{t('loginLog.colTime')}</th>
              <th className="px-4 py-2">{t('loginLog.colIp')}</th>
              <th className="px-4 py-2">{t('loginLog.colResult')}</th>
              <th className="px-4 py-2">{t('loginLog.colUa')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('loading')}</td></tr>}
            {!loading && rows.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('emptyRecords')}</td></tr>}
            {!loading && rows.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-xs text-gray-600 whitespace-nowrap">{formatTime(r.loginTime, i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN')}</td>
                <td className="px-4 py-2 text-xs text-gray-600">{r.clientIp || '—'}</td>
                <td className="px-4 py-2">
                  {r.resultStatus === 'SUCCESS'
                    ? <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">{t('success')}</span>
                    : <span className="rounded bg-red-50 px-1.5 py-0.5 text-xs text-red-700">{t('failed')} {r.failReason ? `(${r.failReason})` : ''}</span>}
                </td>
                <td className="max-w-xs truncate px-4 py-2 text-xs text-gray-500" title={r.userAgent}>{r.userAgent || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {total > size && (
        <div className="mt-2 flex items-center justify-between text-xs text-gray-500">
          <span>{t('totalCount', { count: total })}</span>
          <div className="flex gap-1">
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">{t('prevPage')}</button>
            <span className="px-1">{page}/{totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">{t('nextPage')}</button>
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 操作记录 ====================

function OperationLogsTab({ userId }) {
  const { t, i18n } = useTranslation('userDetail')
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
              <th className="px-4 py-2">{t('opLog.colTime')}</th>
              <th className="px-4 py-2">{t('opLog.colModule')}</th>
              <th className="px-4 py-2">{t('opLog.colOp')}</th>
              <th className="px-4 py-2">{t('opLog.colStatus')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('loading')}</td></tr>}
            {!loading && rows.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">{t('emptyRecords')}</td></tr>}
            {!loading && rows.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 text-xs text-gray-600 whitespace-nowrap">{formatTime(r.createTime, i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN')}</td>
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
          <span>{t('totalCount', { count: total })}</span>
          <div className="flex gap-1">
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">{t('prevPage')}</button>
            <span className="px-1">{page}/{totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border px-2 py-0.5 hover:bg-gray-50 disabled:opacity-40">{t('nextPage')}</button>
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

function formatTime(time, locale) {
  if (!time) return '—'
  const d = new Date(time)
  return d.toLocaleString(locale || 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
