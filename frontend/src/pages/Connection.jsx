import { useEffect, useState, useCallback } from 'react'
import {
  listConnections,
  createConnection,
  updateConnection,
  deleteConnection,
  testConnection,
  testConnectionById,
  updateConnectionStatus,
  setDefaultConnection,
} from '../api/connection'
import {
  Plus, Pencil, Trash2, Plug, Power, Star, X, Search, RefreshCw,
} from 'lucide-react'

const DB_TYPES = ['POSTGRES', 'H2', 'HSQLDB', 'MARIADB', 'MYSQL']

const emptyForm = {
  id: null,
  name: '',
  dbType: 'POSTGRES',
  jdbcUrl: '',
  username: '',
  password: '',
  distributed: false,
  poolConfig: '{"maximumPoolSize":10,"minimumIdle":2}',
  remark: '',
  status: 1,
  isDefault: false,
}

export default function Connection() {
  const [list, setList] = useState([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [testingId, setTestingId] = useState(null)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await listConnections(keyword)
      setList(res.data || [])
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setLoading(false)
    }
  }, [keyword])

  useEffect(() => { load() }, [load])

  const openCreate = () => {
    setForm({ ...emptyForm })
    setModalOpen(true)
  }

  const openEdit = (row) => {
    setForm({ ...row, password: '' })
    setModalOpen(true)
  }

  const closeModal = () => {
    setModalOpen(false)
    setForm(emptyForm)
  }

  const submit = async (e) => {
    e.preventDefault()
    if (!form.name || !form.jdbcUrl || !form.username) {
      showToast('error', '连接名称、JDBC URL、用户名必填')
      return
    }
    setSaving(true)
    try {
      const payload = {
        ...form,
        dbType: form.dbType.toUpperCase(),
        distributed: !!form.distributed,
        isDefault: !!form.isDefault,
        status: Number(form.status) || 1,
      }
      if (!payload.password) delete payload.password
      if (form.id) {
        await updateConnection(form.id, payload)
        showToast('success', '更新成功')
      } else {
        await createConnection(payload)
        showToast('success', '新增成功')
      }
      setModalOpen(false)
      load()
    } catch (err) {
      showToast('error', err.message)
    } finally {
      setSaving(false)
    }
  }

  const onTest = async (row) => {
    setTestingId(row?.id || 'form')
    try {
      const res = row
        ? await testConnectionById(row.id)
        : await testConnection({
            ...form,
            dbType: (form.dbType || '').toUpperCase(),
            password: form.password || undefined,
          })
      const r = res.data
      if (r?.success) {
        showToast('success', `连接成功: ${r.message}`)
      } else {
        showToast('error', `连接失败: ${r?.message || '未知'}`)
      }
    } catch (err) {
      showToast('error', err.message)
    } finally {
      setTestingId(null)
    }
  }

  const onToggleStatus = async (row) => {
    const next = row.status === 1 ? 0 : 1
    try {
      await updateConnectionStatus(row.id, next)
      showToast('success', next === 1 ? '已启用' : '已停用')
      load()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onSetDefault = async (row) => {
    try {
      await setDefaultConnection(row.id)
      showToast('success', `已设为默认: ${row.name}`)
      load()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onDelete = async (row) => {
    if (!window.confirm(`确认删除连接「${row.name}」?`)) return
    try {
      await deleteConnection(row.id)
      showToast('success', '已删除')
      load()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">连接管理</h1>
          <p className="mt-0.5 text-sm text-gray-500">管理图数据库连接配置</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
              placeholder="搜索名称/类型/备注"
              className="w-56 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <button
            onClick={load}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> 刷新
          </button>
          <button
            onClick={openCreate}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
          >
            <Plus size={15} /> 新增连接
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
              <tr>
                <th className="px-4 py-3">名称</th>
                <th className="px-4 py-3">类型</th>
                <th className="px-4 py-3">JDBC URL</th>
                <th className="px-4 py-3">用户名</th>
                <th className="px-4 py-3">distributed</th>
                <th className="px-4 py-3">状态</th>
                <th className="px-4 py-3">默认</th>
                <th className="px-4 py-3">备注</th>
                <th className="px-4 py-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {list.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-4 py-12 text-center text-gray-400">
                    {loading ? '加载中...' : '暂无连接,点击右上角「新增连接」'}
                  </td>
                </tr>
              )}
              {list.map((row) => (
                <tr key={row.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">{row.name}</td>
                  <td className="px-4 py-3">
                    <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">
                      {row.dbType}
                    </span>
                  </td>
                  <td className="max-w-xs truncate px-4 py-3 text-gray-600" title={row.jdbcUrl}>
                    {row.jdbcUrl}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{row.username}</td>
                  <td className="px-4 py-3">
                    {row.distributed
                      ? <span className="text-green-600">true</span>
                      : <span className="text-gray-400">false</span>}
                  </td>
                  <td className="px-4 py-3">
                    {row.status === 1
                      ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">启用</span>
                      : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">停用</span>}
                  </td>
                  <td className="px-4 py-3">
                    {row.isDefault
                      ? <Star size={16} className="fill-amber-400 text-amber-400" />
                      : <span className="text-gray-300">—</span>}
                  </td>
                  <td className="max-w-[160px] truncate px-4 py-3 text-gray-500" title={row.remark}>
                    {row.remark || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <ActionBtn title="测试连接" onClick={() => onTest(row)} disabled={testingId === row.id}>
                        <Plug size={15} />
                      </ActionBtn>
                      <ActionBtn title={row.status === 1 ? '停用' : '启用'} onClick={() => onToggleStatus(row)}>
                        <Power size={15} className={row.status === 1 ? 'text-green-600' : 'text-gray-400'} />
                      </ActionBtn>
                      <ActionBtn
                        title="设为默认"
                        onClick={() => onSetDefault(row)}
                        disabled={row.isDefault}
                      >
                        <Star size={15} className={row.isDefault ? 'fill-amber-400 text-amber-400' : ''} />
                      </ActionBtn>
                      <ActionBtn title="编辑" onClick={() => openEdit(row)}>
                        <Pencil size={15} />
                      </ActionBtn>
                      <ActionBtn title="删除" danger onClick={() => onDelete(row)}>
                        <Trash2 size={15} />
                      </ActionBtn>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
              <h2 className="text-base font-semibold text-gray-800">
                {form.id ? '编辑连接' : '新增连接'}
              </h2>
              <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>

            <form onSubmit={submit} className="max-h-[70vh] overflow-y-auto px-6 py-4">
              <div className="grid grid-cols-2 gap-4">
                <Field label="连接名称" required>
                  <input className={inputCls}
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="如 prod-pg" />
                </Field>
                <Field label="数据库类型" required>
                  <select className={inputCls}
                    value={form.dbType}
                    onChange={(e) => setForm({ ...form, dbType: e.target.value })}>
                    {DB_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </Field>
                <div className="col-span-2">
                  <Field label="JDBC URL" required>
                    <input className={inputCls}
                      value={form.jdbcUrl}
                      onChange={(e) => setForm({ ...form, jdbcUrl: e.target.value })}
                      placeholder="jdbc:postgresql://host:5432/db" />
                  </Field>
                </div>
                <Field label="用户名" required>
                  <input className={inputCls}
                    value={form.username}
                    onChange={(e) => setForm({ ...form, username: e.target.value })} />
                </Field>
                <Field label={form.id ? '密码 (留空则不修改)' : '密码'} required={!form.id}>
                  <input className={inputCls} type="password"
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })} />
                </Field>
                <Field label="连接池配置 (JSON)">
                  <textarea className={inputCls} rows={2}
                    value={form.poolConfig}
                    onChange={(e) => setForm({ ...form, poolConfig: e.target.value })} />
                </Field>
                <Field label="备注">
                  <input className={inputCls}
                    value={form.remark}
                    onChange={(e) => setForm({ ...form, remark: e.target.value })} />
                </Field>
                <div className="col-span-2 flex items-center gap-6 pt-2">
                  <label className="flex items-center gap-2 text-sm text-gray-700">
                    <input type="checkbox"
                      checked={!!form.distributed}
                      onChange={(e) => setForm({ ...form, distributed: e.target.checked })} />
                    distributed = true
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-700">
                    <input type="checkbox"
                      checked={Number(form.status) === 1}
                      onChange={(e) => setForm({ ...form, status: e.target.checked ? 1 : 0 })} />
                    启用
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-700">
                    <input type="checkbox"
                      checked={!!form.isDefault}
                      onChange={(e) => setForm({ ...form, isDefault: e.target.checked })} />
                    设为默认
                  </label>
                </div>
              </div>
            </form>

            <div className="flex items-center justify-between border-t border-gray-200 bg-gray-50 px-6 py-3">
              <button
                type="button"
                onClick={() => onTest(null)}
                disabled={testingId === 'form'}
                className="flex items-center gap-1 rounded-md border border-indigo-200 bg-white px-3 py-1.5 text-sm text-indigo-600 hover:bg-indigo-50 disabled:opacity-50"
              >
                <Plug size={15} className={testingId === 'form' ? 'animate-pulse' : ''} />
                测试连接
              </button>
              <div className="flex gap-2">
                <button onClick={closeModal}
                  className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                  取消
                </button>
                <button onClick={submit} disabled={saving}
                  className="rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
                  {saving ? '保存中...' : '保存'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'

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

function ActionBtn({ title, onClick, disabled, danger, children }) {
  return (
    <button
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`flex h-7 w-7 items-center justify-center rounded transition-colors
        ${danger
          ? 'text-gray-500 hover:bg-red-50 hover:text-red-600'
          : 'text-gray-500 hover:bg-indigo-50 hover:text-indigo-600'}
        ${disabled ? 'cursor-not-allowed opacity-40' : ''}`}
    >
      {children}
    </button>
  )
}
