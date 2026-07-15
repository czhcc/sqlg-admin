import { useEffect, useState, useCallback } from 'react'
import { useAuth } from '../context/AuthContext'
import {
  listEdgeTypeConnections,
  setActiveConnection,
  listEdgeTypes,
  getEdgeTypeDetail,
  createEdgeType,
  deleteEdgeType,
  clearEdges,
  getTableSchema,
  listVertexLabels,
  getGremlinExamples,
  getSqlExamples,
} from '../api/edgeType'
import {
  Plus, Trash2, Eraser, Database, Star, ChevronDown,
  RefreshCw, Key, Table2, Terminal, FileCode,
  X, Search, Eye, ArrowRight, GitFork,
} from 'lucide-react'

const emptyForm = {
  schema: 'public',
  outLabel: '',
  inLabel: '',
  label: '',
  identifier: '',
}

export default function EdgeType() {
  const { hasOp } = useAuth()
  const [connections, setConnections] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [list, setList] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [toast, setToast] = useState(null)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [idStrategy, setIdStrategy] = useState('auto')
  const [vertexLabels, setVertexLabels] = useState([])
  const [vertexLabelsLoading, setVertexLabelsLoading] = useState(false)

  const [detailModal, setDetailModal] = useState(null)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listEdgeTypeConnections()
      const cl = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(cl)
      if (cl.length > 0) {
        const fallback = cl.find((c) => c.isDefault) || cl[0]
        const initial = remembered != null && cl.some((c) => c.id === remembered)
          ? remembered : fallback.id
        setSelectedId((prev) => (prev == null ? initial : prev))
      } else {
        setSelectedId(null)
      }
    } catch (e) {
      showToast('error', e.message)
    }
  }, [])

  const loadList = useCallback(async () => {
    if (!selectedId) { setList([]); return }
    setLoading(true)
    try {
      const res = await listEdgeTypes(selectedId)
      setList(res.data || [])
    } catch (e) {
      showToast('error', e.message)
      setList([])
    } finally {
      setLoading(false)
    }
  }, [selectedId])

  useEffect(() => { loadConnections() }, [loadConnections])
  useEffect(() => { loadList() }, [loadList])

  const selected = connections.find((c) => c.id === selectedId)

  const onSelect = async (id) => {
    setSelectedId(id)
    setDropdownOpen(false)
    try { await setActiveConnection(id) } catch (_) {}
  }

  const filtered = list.filter((r) => {
    if (!keyword) return true
    const kw = keyword.toLowerCase()
    return r.label?.toLowerCase().includes(kw)
      || r.schema?.toLowerCase().includes(kw)
      || r.tableName?.toLowerCase().includes(kw)
      || r.outVertexLabels?.some((v) => v.toLowerCase().includes(kw))
      || r.inVertexLabels?.some((v) => v.toLowerCase().includes(kw))
  })

  // ==================== 新增表单 ====================

  const openCreate = async () => {
    setForm({ ...emptyForm })
    setIdStrategy('auto')
    setModalOpen(true)
    if (vertexLabels.length === 0) {
      setVertexLabelsLoading(true)
      try {
        const res = await listVertexLabels(selectedId)
        setVertexLabels(res.data || [])
      } catch (e) {
        showToast('error', '加载点类型失败: ' + e.message)
      } finally {
        setVertexLabelsLoading(false)
      }
    }
  }

  const closeModal = () => {
    setModalOpen(false)
  }

  const submit = async () => {
    if (!form.label.trim()) { showToast('error', 'EdgeLabel 名称不能为空'); return }
    if (!form.outLabel) { showToast('error', '请选择出点类型'); return }
    if (!form.inLabel) { showToast('error', '请选择入点类型'); return }

    const identifiers = idStrategy === 'custom'
      ? (form.identifier && form.identifier.trim() ? [form.identifier.trim()] : [])
      : []
    if (idStrategy === 'custom' && identifiers.length === 0) {
      showToast('error', '选择字符串 ID 时必须填写字段名')
      return
    }

    setSaving(true)
    try {
      await createEdgeType(selectedId, {
        schema: form.schema || 'public',
        outLabel: form.outLabel,
        inLabel: form.inLabel,
        label: form.label.trim(),
        identifiers,
      })
      showToast('success', '新增成功')
      setModalOpen(false)
      loadList()
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setSaving(false)
    }
  }

  // ==================== 行操作 ====================

  const onClearData = async (row) => {
    const count = row.edgeCount || 0
    const msg = count > 0
      ? `确认清空「${row.schema}.${row.label}」的 ${count} 条边数据?\n\n⚠️ 仅删除边数据,保留表结构 E_${row.label} 和 EdgeLabel 定义。`
      : `确认清空「${row.schema}.${row.label}」?\n(当前无边数据)\n\n⚠️ 仅删除边数据,保留表结构。`
    if (!window.confirm(msg)) return
    try {
      const res = await clearEdges(selectedId, row.schema, row.label)
      showToast('success', `已清空 ${res.data?.deleted || 0} 条边数据`)
      loadList()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onDelete = async (row) => {
    if (!window.confirm(
      `确认删除边类型「${row.schema}.${row.label}」?\n\n` +
      `🔴 此操作将永久删除:\n` +
      `   • EdgeLabel 定义\n` +
      `   • 底层物理表 ${row.tableName}\n` +
      `   • 该表的所有边数据\n\n` +
      `此操作不可恢复!`
    )) return
    try {
      await deleteEdgeType(selectedId, row.schema, row.label)
      showToast('success', '已删除边类型及底层表')
      loadList()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onViewDetail = async (row) => {
    try {
      const res = await getEdgeTypeDetail(selectedId, row.schema, row.label)
      setDetailModal({ type: 'detail', title: `${row.schema}.${row.label} — 详情`, data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewTableSchema = async (row) => {
    try {
      const res = await getTableSchema(selectedId, row.schema, row.label)
      setDetailModal({ type: 'tableSchema', title: `${row.tableName} — 表结构`, data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewGremlin = async (row) => {
    try {
      const res = await getGremlinExamples(row.schema, row.label)
      setDetailModal({ type: 'examples', title: `${row.label} — Gremlin 示例`, data: res.data, lang: 'gremlin' })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewSql = async (row) => {
    try {
      const res = await getSqlExamples(row.schema, row.label)
      setDetailModal({ type: 'examples', title: `${row.tableName} — SQL 示例`, data: res.data, lang: 'sql' })
    } catch (e) { showToast('error', e.message) }
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">边类型管理</h1>
          <p className="mt-0.5 text-sm text-gray-500">管理 EdgeLabel 定义、出入点类型、属性和数据</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索 Schema/Label/点类型"
              className="w-52 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <div className="relative">
            <button
              onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-pink-500" />
                <span className="truncate font-medium">{selected ? selected.name : '选择连接'}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && (
                    <div className="px-3 py-2 text-sm text-gray-400">暂无可用连接</div>
                  )}
                  {connections.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-pink-50 ${
                        c.id === selectedId ? 'bg-pink-50 text-pink-700' : 'text-gray-700'
                      }`}
                    >
                      <span className="flex items-center gap-2 truncate">
                        <Database size={14} className="text-gray-400" />
                        <span className="truncate">{c.name}</span>
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{c.dbType}</span>
                        {c.isDefault && <Star size={12} className="fill-amber-400 text-amber-400" />}
                      </span>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
          <button
            onClick={loadList}
            disabled={!selectedId || loading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> 刷新
          </button>
          {hasOp('edge_type:create') && (
            <button
              onClick={openCreate}
              disabled={!selectedId}
              className="flex items-center gap-1 rounded-md bg-pink-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50"
            >
              <Plus size={15} /> 新增边类型
            </button>
          )}
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        {!selectedId && (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            请从右上角选择一个图数据库连接
          </div>
        )}
        {selectedId && (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-4 py-3">Schema</th>
                  <th className="px-4 py-3">EdgeLabel</th>
                  <th className="px-4 py-3">底层表名</th>
                  <th className="px-4 py-3">出点类型</th>
                  <th className="px-4 py-3">入点类型</th>
                  <th className="px-4 py-3 text-center">属性数</th>
                  <th className="px-4 py-3">Identifier</th>
                  <th className="px-4 py-3 text-center">索引数</th>
                  <th className="px-4 py-3 text-center">边数据量</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={10} className="px-4 py-12 text-center text-gray-400">
                      {loading ? '加载中...' : '暂无边类型,点击右上角「新增边类型」'}
                    </td>
                  </tr>
                )}
                {filtered.map((row) => (
                  <tr key={`${row.schema}.${row.label}`} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-700">{row.schema}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 font-medium text-gray-800">
                        <GitFork size={13} className="text-pink-400" /> {row.label}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">{row.tableName}</code>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {(row.outVertexLabels || []).map((v, i) => (
                          <span key={i} className="inline-flex items-center gap-0.5 rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">
                            {v}
                          </span>
                        ))}
                        {(row.outVertexLabels || []).length === 0 && <span className="text-gray-300">—</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {(row.inVertexLabels || []).map((v, i) => (
                          <span key={i} className="inline-flex items-center gap-0.5 rounded bg-pink-50 px-1.5 py-0.5 text-xs text-pink-700">
                            {v}
                          </span>
                        ))}
                        {(row.inVertexLabels || []).length === 0 && <span className="text-gray-300">—</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">{row.propertyCount}</span>
                    </td>
                    <td className="px-4 py-3">
                      {(row.identifiers || []).length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {row.identifiers.map((id) => (
                            <span key={id} className="inline-flex items-center gap-0.5 rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-700">
                              <Key size={10} /> {id}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-gray-300">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center text-gray-600">{row.indexCount}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={row.edgeCount > 0 ? 'font-medium text-gray-800' : 'text-gray-300'}>
                        {row.edgeCount?.toLocaleString() || 0}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <ActionBtn title="查看详情" onClick={() => onViewDetail(row)}>
                          <Eye size={15} />
                        </ActionBtn>
                        <ActionBtn title="查看表结构" onClick={() => onViewTableSchema(row)}>
                          <Table2 size={15} />
                        </ActionBtn>
                        <ActionBtn title="Gremlin 示例" onClick={() => onViewGremlin(row)}>
                          <Terminal size={15} />
                        </ActionBtn>
                        <ActionBtn title="SQL 示例" onClick={() => onViewSql(row)}>
                          <FileCode size={15} />
                        </ActionBtn>
                        {hasOp('edge_data:clear') && <ActionBtn title="清空边数据(保留表结构)" warning onClick={() => onClearData(row)}>
                          <Eraser size={15} />
                        </ActionBtn>}
                        {hasOp('edge_type:delete') && <ActionBtn title="删除边类型(删除表+数据)" danger onClick={() => onDelete(row)}>
                          <Trash2 size={15} />
                        </ActionBtn>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {modalOpen && (
        <CreateEdgeModal
          form={form}
          setForm={setForm}
          idStrategy={idStrategy}
          setIdStrategy={setIdStrategy}
          saving={saving}
          vertexLabels={vertexLabels}
          vertexLabelsLoading={vertexLabelsLoading}
          onClose={closeModal}
          onSubmit={submit}
        />
      )}

      {detailModal && (
        <DetailModal data={detailModal} onClose={() => setDetailModal(null)} />
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white'
          : toast.type === 'error' ? 'bg-red-600 text-white'
          : 'bg-gray-700 text-white'
        }`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

// ==================== 新增边类型 Modal ====================

function CreateEdgeModal({
  form, setForm, idStrategy, setIdStrategy,
  vertexLabels, vertexLabelsLoading, saving,
  onClose, onSubmit,
}) {
  const schemas = Array.from(new Set(vertexLabels.map((v) => v.schema))).sort()
  const labelsInSchema = vertexLabels.filter((v) => v.schema === form.schema)
  const edgeLabel = form.label || 'edge'

  const gremlinPreview = form.outLabel && form.inLabel
    ? `g.V().hasLabel('${form.outLabel}').has('name','张三')\n` +
      ` .as('a')\n` +
      ` .V().hasLabel('${form.inLabel}').has('name','某对象')\n` +
      ` .addE('${edgeLabel}')\n` +
      ` .from('a')`
    : ''

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[92vh] w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">新增边类型</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        <div className="max-h-[68vh] overflow-y-auto px-6 py-4">
          <div className="grid grid-cols-3 gap-4">
            <Field label="Schema" required>
              <select
                className={inputCls}
                value={form.schema}
                onChange={(e) => setForm({
                  ...form,
                  schema: e.target.value,
                  outLabel: '',
                  inLabel: '',
                })}
              >
                {schemas.length === 0 && <option value="">-- 无 --</option>}
                {schemas.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </Field>
            <Field label="出点类型 (Out VertexLabel)" required>
              <LabelSelect
                value={form.outLabel}
                options={labelsInSchema}
                loading={vertexLabelsLoading}
                onChange={(v) => setForm({ ...form, outLabel: v })}
              />
            </Field>
            <Field label="入点类型 (In VertexLabel)" required>
              <LabelSelect
                value={form.inLabel}
                options={labelsInSchema}
                loading={vertexLabelsLoading}
                onChange={(v) => setForm({ ...form, inLabel: v })}
              />
            </Field>
          </div>

          <div className="mt-4">
            <Field label="EdgeLabel 名称" required>
              <input
                className={inputCls}
                value={form.label}
                onChange={(e) => setForm({ ...form, label: e.target.value })}
                placeholder="如 knows, worksFor, transferTo"
              />
            </Field>
          </div>

          <div className="mt-4">
            <label className="mb-2 block text-sm font-medium text-gray-700">ID 策略</label>
            <div className="space-y-2">
              <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-sm transition-colors ${
                idStrategy === 'auto' ? 'border-pink-300 bg-pink-50 text-pink-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}>
                <input type="radio" className="accent-pink-600"
                  checked={idStrategy === 'auto'}
                  onChange={() => { setIdStrategy('auto'); setForm({ ...form, identifier: '' }) }} />
                <Key size={15} className={idStrategy === 'auto' ? 'text-pink-500' : 'text-gray-400'} />
                <span className="font-medium">默认自增 ID</span>
                <span className="text-xs text-gray-400">(BIGINT, sqlg 自动生成)</span>
              </label>
              <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-sm transition-colors ${
                idStrategy === 'custom' ? 'border-pink-300 bg-pink-50 text-pink-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}>
                <input type="radio" className="accent-pink-600"
                  checked={idStrategy === 'custom'}
                  onChange={() => setIdStrategy('custom')} />
                <Key size={15} className={idStrategy === 'custom' ? 'text-pink-500' : 'text-gray-400'} />
                <span className="font-medium">字符串 ID</span>
                <span className="text-xs text-gray-400">(自定义业务主键)</span>
              </label>
              {idStrategy === 'custom' && (
                <div className="pl-7">
                  <input
                    className={inputCls}
                    value={form.identifier}
                    onChange={(e) => setForm({ ...form, identifier: e.target.value })}
                    placeholder="identifier 字段名,如 edge_id"
                  />
                  <p className="mt-1 text-xs text-gray-400">
                    将自动创建该名称的 STRING 属性并标记为业务主键
                  </p>
                </div>
              )}
            </div>
          </div>

          <div className="mt-5 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400">边方向预览</div>
            <div className="flex items-center justify-center gap-3 py-2">
              <span className="rounded-lg bg-indigo-50 px-3 py-2 text-sm font-medium text-indigo-700">
                {form.outLabel || '出点'}
              </span>
              <div className="flex flex-col items-center">
                <ArrowRight size={20} className="text-pink-500" />
                <span className="mt-0.5 text-xs font-medium text-pink-600">{edgeLabel}</span>
              </div>
              <span className="rounded-lg bg-pink-50 px-3 py-2 text-sm font-medium text-pink-700">
                {form.inLabel || '入点'}
              </span>
            </div>
          </div>

          {gremlinPreview && (
            <div className="mt-3">
              <div className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-gray-400">Gremlin 示例</div>
              <pre className="overflow-x-auto rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                <code className="text-indigo-700">{gremlinPreview}</code>
              </pre>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            取消
          </button>
          <button onClick={onSubmit} disabled={saving}
            className="rounded-md bg-pink-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50">
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  )
}

function LabelSelect({ value, options, loading, onChange }) {
  return (
    <select
      className={inputCls}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">-- 请选择 --</option>
      {loading && <option disabled>加载中...</option>}
      {options.map((v) => (
        <option key={v.label} value={v.label}>
          {v.label}
        </option>
      ))}
    </select>
  )
}

// ==================== 详情 Modal ====================

function DetailModal({ data, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[85vh] w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">{data.title}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto px-6 py-4">
          {data.type === 'detail' && <EdgeDetail detail={data.data} />}
          {data.type === 'tableSchema' && <TableSchemaDetail columns={data.data} />}
          {data.type === 'examples' && <ExamplesDetail examples={data.data} lang={data.lang} />}
        </div>
      </div>
    </div>
  )
}

function EdgeDetail({ detail }) {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-4 text-sm">
        <Info label="Schema" value={detail.schema} />
        <Info label="Label" value={detail.label} />
        <Info label="表名" value={detail.tableName} mono />
        <Info label="边数量" value={(detail.edgeCount || 0).toLocaleString()} />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <h3 className="mb-2 text-sm font-semibold text-gray-700">出点类型 ({detail.outVertexLabels?.length || 0})</h3>
          <div className="space-y-1">
            {(detail.outVertexLabels || []).length === 0 && <span className="text-xs text-gray-300">无</span>}
            {detail.outVertexLabels?.map((v) => (
              <div key={v.fullName} className="flex items-center gap-1 text-sm text-gray-600">
                <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">{v.fullName}</span>
              </div>
            ))}
          </div>
        </div>
        <div>
          <h3 className="mb-2 text-sm font-semibold text-gray-700">入点类型 ({detail.inVertexLabels?.length || 0})</h3>
          <div className="space-y-1">
            {(detail.inVertexLabels || []).length === 0 && <span className="text-xs text-gray-300">无</span>}
            {detail.inVertexLabels?.map((v) => (
              <div key={v.fullName} className="flex items-center gap-1 text-sm text-gray-600">
                <span className="rounded bg-pink-50 px-1.5 py-0.5 text-xs text-pink-700">{v.fullName}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-gray-700">属性 ({detail.properties?.length || 0})</h3>
        <div className="overflow-hidden rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr><th className="px-3 py-2">属性名</th><th className="px-3 py-2">类型</th><th className="px-3 py-2">Identifier</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(detail.properties || []).length === 0 && (
                <tr><td colSpan={3} className="px-3 py-4 text-center text-gray-400">无属性</td></tr>
              )}
              {detail.properties?.map((p) => (
                <tr key={p.name}>
                  <td className="px-3 py-2 font-medium text-gray-700">{p.name}</td>
                  <td className="px-3 py-2"><span className="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">{p.type}</span></td>
                  <td className="px-3 py-2">{detail.identifiers?.includes(p.name) ? <Key size={14} className="text-amber-500" /> : <span className="text-gray-300">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {(detail.indexes?.length > 0) && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-gray-700">索引 ({detail.indexes.length})</h3>
          <div className="space-y-1">
            {detail.indexes.map((idx) => (
              <div key={idx.name} className="flex items-center gap-2 rounded-md border border-gray-100 px-3 py-2 text-sm">
                <span className="font-medium text-gray-700">{idx.name}</span>
                <span className={`rounded px-1.5 py-0.5 text-xs ${idx.indexType === 'UNIQUE' ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'}`}>{idx.indexType}</span>
                <span className="text-gray-500">({idx.properties?.join(', ')})</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function TableSchemaDetail({ columns }) {
  return (
    <div className="overflow-hidden rounded-lg border border-gray-200">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
          <tr>
            <th className="px-3 py-2">列名</th>
            <th className="px-3 py-2">数据类型</th>
            <th className="px-3 py-2 text-center">可空</th>
            <th className="px-3 py-2">默认值</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {columns.length === 0 && (
            <tr><td colSpan={4} className="px-3 py-6 text-center text-gray-400">表不存在或无列信息</td></tr>
          )}
          {columns.map((c) => (
            <tr key={c.columnName}>
              <td className="px-3 py-2 font-medium text-gray-700">{c.columnName}</td>
              <td className="px-3 py-2"><span className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">{c.dataType}</span></td>
              <td className="px-3 py-2 text-center">{c.nullable ? <span className="text-green-600">YES</span> : <span className="text-red-500">NO</span>}</td>
              <td className="px-3 py-2 text-gray-500">{c.defaultValue || <span className="text-gray-300">—</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ExamplesDetail({ examples, lang }) {
  const [copied, setCopied] = useState(null)
  const copy = (code, idx) => {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(idx)
      setTimeout(() => setCopied(null), 1500)
    })
  }
  return (
    <div className="space-y-2">
      {examples.map((ex, i) => (
        <div key={i} className="rounded-lg border border-gray-200">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <span className="text-sm font-medium text-gray-700">{ex.title}</span>
            <button
              onClick={() => copy(ex.code, i)}
              className="rounded border border-gray-200 px-2 py-0.5 text-xs text-gray-500 hover:bg-gray-50"
            >
              {copied === i ? '已复制' : '复制'}
            </button>
          </div>
          <pre className="overflow-x-auto px-3 py-2 text-sm">
            <code className={lang === 'gremlin' ? 'text-indigo-700' : 'text-blue-700'}>{ex.code}</code>
          </pre>
        </div>
      ))}
    </div>
  )
}

// ==================== 共享组件 ====================

const inputCls = 'flex-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-pink-500 focus:ring-1 focus:ring-pink-100'

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

function ActionBtn({ title, onClick, disabled, danger, warning, children }) {
  return (
    <button
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`flex h-7 w-7 items-center justify-center rounded transition-colors
        ${danger
          ? 'text-gray-500 hover:bg-red-50 hover:text-red-600'
          : warning
            ? 'text-gray-500 hover:bg-amber-50 hover:text-amber-600'
            : 'text-gray-500 hover:bg-pink-50 hover:text-pink-600'}
        ${disabled ? 'cursor-not-allowed opacity-40' : ''}`}
    >
      {children}
    </button>
  )
}

function Info({ label, value, mono }) {
  return (
    <div>
      <span className="text-xs text-gray-400">{label}: </span>
      <span className={`font-medium text-gray-700 ${mono ? 'font-mono' : ''}`}>{value}</span>
    </div>
  )
}
