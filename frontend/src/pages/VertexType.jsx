import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import {
  listVertexTypeConnections,
  setActiveConnection,
  listVertexTypes,
  getVertexTypeDetail,
  createVertexType,
  updateVertexType,
  clearVertices,
  deleteVertexType,
  getTableSchema,
  getGremlinExamples,
  getSqlExamples,
} from '../api/vertexType'
import {
  Plus, Pencil, Trash2, Eraser, Database, Star, ChevronDown,
  RefreshCw, Settings2, Key, Table2, GitFork, Terminal, FileCode,
  X, Search, Eye,
} from 'lucide-react'

const emptyForm = {
  schema: 'public',
  label: '',
  identifiers: [],
}

export default function VertexType() {
  const { t } = useTranslation('vertexType')
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
  const [editingTarget, setEditingTarget] = useState(null)
  const [existingProperties, setExistingProperties] = useState([])
  const [loadingDetail, setLoadingDetail] = useState(false)

  const [detailModal, setDetailModal] = useState(null)
  const [idStrategy, setIdStrategy] = useState('auto')

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listVertexTypeConnections()
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
      const res = await listVertexTypes(selectedId)
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
  })

  const openCreate = () => {
    setForm({ ...emptyForm })
    setIdStrategy('auto')
    setEditingTarget(null)
    setExistingProperties([])
    setModalOpen(true)
  }

  const openEdit = async (row) => {
    setForm({
      schema: row.schema,
      label: row.label,
      identifiers: row.identifiers || [],
    })
    setEditingTarget({ schema: row.schema, label: row.label })
    setExistingProperties([])
    setModalOpen(true)
    setLoadingDetail(true)
    try {
      const res = await getVertexTypeDetail(selectedId, row.schema, row.label)
      setExistingProperties(res.data?.properties || [])
    } catch (e) {
      showToast('error', t('msg.loadPropsFail', { message: e.message }))
    } finally {
      setLoadingDetail(false)
    }
  }

  const closeModal = () => {
    setModalOpen(false)
    setEditingTarget(null)
    setExistingProperties([])
  }

  const submit = async () => {
    if (!form.label.trim()) { showToast('error', t('msg.labelRequired')); return }

    setSaving(true)
    try {
      if (editingTarget) {
        if (form.label.trim() === editingTarget.label) {
          showToast('info', t('msg.labelUnchanged'))
          setModalOpen(false)
          return
        }
        await updateVertexType(selectedId, {
          schema: editingTarget.schema,
          label: form.label.trim(),
          originalSchema: editingTarget.schema,
          originalLabel: editingTarget.label,
        })
        showToast('success', t('msg.renamed'))
      } else {
        const identifiers = idStrategy === 'custom'
          ? form.identifiers.filter((n) => n && n.trim())
          : []
        if (idStrategy === 'custom' && identifiers.length === 0) {
          showToast('error', t('msg.stringIdRequired'))
          setSaving(false)
          return
        }
        await createVertexType(selectedId, {
          schema: form.schema || 'public',
          label: form.label.trim(),
          identifiers,
        })
        showToast('success', t('msg.created'))
      }
      setModalOpen(false)
      loadList()
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setSaving(false)
    }
  }

  const onClearData = async (row) => {
    const count = row.vertexCount || 0
    const msg = count > 0
      ? t('msg.confirmClear', { schema: row.schema, label: row.label, count, table: row.label })
      : t('msg.confirmClearEmpty', { schema: row.schema, label: row.label })
    if (!window.confirm(msg)) return
    try {
      const res = await clearVertices(selectedId, row.schema, row.label)
      showToast('success', t('msg.cleared', { count: res.data?.deleted || 0 }))
      loadList()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onDelete = async (row) => {
    if (!window.confirm(t('msg.confirmDelete', { schema: row.schema, label: row.label, table: row.tableName }))) return
    try {
      await deleteVertexType(selectedId, row.schema, row.label)
      showToast('success', t('msg.deleted'))
      loadList()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onViewProperties = async (row) => {
    try {
      const res = await getVertexTypeDetail(selectedId, row.schema, row.label)
      setDetailModal({ type: 'properties', title: t('propDetailTitle', { schema: row.schema, label: row.label }), data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewTableSchema = async (row) => {
    try {
      const res = await getTableSchema(selectedId, row.schema, row.label)
      setDetailModal({ type: 'tableSchema', title: t('tableSchemaTitle', { tableName: row.tableName }), data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewGremlin = async (row) => {
    try {
      const res = await getGremlinExamples(row.schema, row.label)
      setDetailModal({ type: 'examples', title: t('gremlinTitle', { label: row.label }), data: res.data, lang: 'gremlin' })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewSql = async (row) => {
    try {
      const res = await getSqlExamples(row.schema, row.label)
      setDetailModal({ type: 'examples', title: t('sqlTitle', { tableName: row.tableName }), data: res.data, lang: 'sql' })
    } catch (e) { showToast('error', e.message) }
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">{t('title')}</h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder={t('searchPlaceholder')}
              className="w-44 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <div className="relative">
            <button
              onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-indigo-500" />
                <span className="truncate font-medium">{selected ? selected.name : t('common:selectConnection')}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && (
                    <div className="px-3 py-2 text-sm text-gray-400">{t('common:noConnection')}</div>
                  )}
                  {connections.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-indigo-50 ${
                        c.id === selectedId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'
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
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
          {hasOp('vertex_type:create') && (
            <button
              onClick={openCreate}
              disabled={!selectedId}
              className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              <Plus size={15} /> {t('addButton')}
            </button>
          )}
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        {!selectedId && (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">
            {t('common:pleaseSelectConnection')}
          </div>
        )}
        {selectedId && (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-4 py-3">{t('col.schema')}</th>
                  <th className="px-4 py-3">{t('col.vertexLabel')}</th>
                  <th className="px-4 py-3">{t('col.tableName')}</th>
                  <th className="px-4 py-3 text-center">{t('col.propertyCount')}</th>
                  <th className="px-4 py-3">{t('col.identifier')}</th>
                  <th className="px-4 py-3 text-center">{t('col.indexCount')}</th>
                  <th className="px-4 py-3 text-center">{t('col.vertexCount')}</th>
                  <th className="px-4 py-3">{t('col.status')}</th>
                  <th className="px-4 py-3 text-right">{t('common:actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={9} className="px-4 py-12 text-center text-gray-400">
                      {loading ? t('common:loading') : t('empty')}
                    </td>
                  </tr>
                )}
                {filtered.map((row) => (
                  <tr key={`${row.schema}.${row.label}`} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-700">{row.schema}</td>
                    <td className="px-4 py-3 font-medium text-gray-800">{row.label}</td>
                    <td className="px-4 py-3">
                      <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">{row.tableName}</code>
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
                      <span className={row.vertexCount > 0 ? 'font-medium text-gray-800' : 'text-gray-300'}>
                        {row.vertexCount?.toLocaleString() || 0}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {row.status === 'active'
                        ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">active</span>
                        : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">empty</span>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <ActionBtn title={t('action.viewProperties')} onClick={() => onViewProperties(row)}>
                          <Eye size={15} />
                        </ActionBtn>
                        <ActionBtn title={t('action.viewTableSchema')} onClick={() => onViewTableSchema(row)}>
                          <Table2 size={15} />
                        </ActionBtn>
                        <ActionBtn title={t('action.gremlinExamples')} onClick={() => onViewGremlin(row)}>
                          <Terminal size={15} />
                        </ActionBtn>
                        <ActionBtn title={t('action.sqlExamples')} onClick={() => onViewSql(row)}>
                          <FileCode size={15} />
                        </ActionBtn>
                        {hasOp('vertex_type:update') && <ActionBtn title={t('common:edit')} onClick={() => openEdit(row)}>
                          <Pencil size={15} />
                        </ActionBtn>}
                        {hasOp('vertex_data:clear') && <ActionBtn title={t('action.clearVertices')} warning onClick={() => onClearData(row)}>
                          <Eraser size={15} />
                        </ActionBtn>}
                        {hasOp('vertex_type:delete') && <ActionBtn title={t('action.deleteType')} danger onClick={() => onDelete(row)}>
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
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
              <h2 className="text-base font-semibold text-gray-800">
                {editingTarget ? t('editTitle') : t('createTitle')}
              </h2>
              <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>

            <div className="max-h-[60vh] overflow-y-auto px-6 py-4">
              <div className="grid grid-cols-2 gap-4">
                <Field label={t('col.schema')} required={!editingTarget}>
                  {editingTarget ? (
                    <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600">{form.schema}</div>
                  ) : (
                    <input className={inputCls}
                      value={form.schema}
                      onChange={(e) => setForm({ ...form, schema: e.target.value })}
                      placeholder="public" />
                  )}
                </Field>
                <Field label={t('labelName')} required>
                  <input className={inputCls}
                    value={form.label}
                    onChange={(e) => setForm({ ...form, label: e.target.value })}
                    placeholder={t('labelPlaceholder')} />
                </Field>
              </div>

              {editingTarget ? (
                <div className="mt-4">
                  <div className="mb-1.5 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-gray-400">
                    <span>{t('propListTitle')}{existingProperties.length > 0 && ` (${existingProperties.length})`}</span>
                    <span className="font-normal normal-case text-gray-400">{t('propListReadonly')}</span>
                  </div>
                  {loadingDetail && <div className="py-4 text-center text-xs text-gray-400">{t('common:loading')}</div>}
                  {!loadingDetail && existingProperties.length === 0 && (
                    <div className="py-4 text-center text-xs text-gray-400">{t('noPropsOnLabel')}</div>
                  )}
                  <div className="space-y-1.5">
                    {existingProperties.map((p) => (
                      <div key={p.name} className="flex items-center gap-2 rounded-md border border-gray-100 bg-gray-50 px-3 py-2">
                        <span className="flex-1 truncate text-sm font-medium text-gray-600">{p.name}</span>
                        <span className={`rounded px-2 py-0.5 text-xs font-medium ${
                          p.type === 'JSON' ? 'bg-purple-100 text-purple-700'
                          : p.type === 'INTEGER' || p.type === 'LONG' ? 'bg-blue-100 text-blue-700'
                          : p.type === 'BOOLEAN' ? 'bg-green-100 text-green-700'
                          : 'bg-gray-200 text-gray-600'
                        }`}>{p.type}</span>
                        {form.identifiers.includes(p.name) && (
                          <span className="inline-flex items-center gap-0.5 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] text-amber-700">
                            <Key size={9} /> ID
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="mt-4">
                  <label className="mb-2 block text-sm font-medium text-gray-700">{t('idStrategy')}</label>
                  <div className="space-y-2">
                    <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-sm transition-colors ${
                      idStrategy === 'auto' ? 'border-indigo-300 bg-indigo-50 text-indigo-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                    }`}>
                      <input type="radio" className="accent-indigo-600"
                        checked={idStrategy === 'auto'}
                        onChange={() => { setIdStrategy('auto'); setForm({ ...form, identifiers: [] }) }} />
                      <Key size={15} className={idStrategy === 'auto' ? 'text-indigo-500' : 'text-gray-400'} />
                      <span className="font-medium">{t('autoId')}</span>
                      <span className="text-xs text-gray-400">{t('autoIdHint')}</span>
                    </label>
                    <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-sm transition-colors ${
                      idStrategy === 'custom' ? 'border-indigo-300 bg-indigo-50 text-indigo-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                    }`}>
                      <input type="radio" className="accent-indigo-600"
                        checked={idStrategy === 'custom'}
                        onChange={() => setIdStrategy('custom')} />
                      <Key size={15} className={idStrategy === 'custom' ? 'text-indigo-500' : 'text-gray-400'} />
                      <span className="font-medium">{t('stringId')}</span>
                      <span className="text-xs text-gray-400">{t('stringIdHint')}</span>
                    </label>
                    {idStrategy === 'custom' && (
                      <div className="pl-7">
                        <input
                          className={inputCls}
                          value={form.identifiers[0] || ''}
                          onChange={(e) => setForm({
                            ...form,
                            identifiers: e.target.value ? [e.target.value] : [],
                          })}
                          placeholder={t('identifierPlaceholder')}
                        />
                        <p className="mt-1 text-xs text-gray-400">
                          {t('identifierHint')}
                        </p>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
              <button onClick={closeModal}
                className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                {t('common:cancel')}
              </button>
              <button onClick={submit} disabled={saving}
                className="rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
                {saving ? t('common:saving') : t('common:save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {detailModal && (
        <DetailModal data={detailModal} onClose={() => setDetailModal(null)} />
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
          {data.type === 'properties' && <PropertiesDetail detail={data.data} />}
          {data.type === 'tableSchema' && <TableSchemaDetail columns={data.data} />}
          {data.type === 'examples' && <ExamplesDetail examples={data.data} lang={data.lang} />}
        </div>
      </div>
    </div>
  )
}

function PropertiesDetail({ detail }) {
  const { t } = useTranslation('vertexType')
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-4 text-sm">
        <Info label={t('col.schema')} value={detail.schema} />
        <Info label={t('col.vertexLabel')} value={detail.label} />
        <Info label={t('detail.tableName')} value={detail.tableName} mono />
        <Info label={t('detail.vertexCount')} value={(detail.vertexCount || 0).toLocaleString()} />
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-gray-700">{t('detail.propsTitle', { count: detail.properties?.length || 0 })}</h3>
        <div className="overflow-hidden rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr><th className="px-3 py-2">{t('detail.propName')}</th><th className="px-3 py-2">{t('detail.propType')}</th><th className="px-3 py-2">{t('detail.identifier')}</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
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
          <h3 className="mb-2 text-sm font-semibold text-gray-700">{t('detail.indexesTitle', { count: detail.indexes.length })}</h3>
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

      {(detail.inEdgeLabels?.length > 0 || detail.outEdgeLabels?.length > 0) && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <h3 className="mb-2 text-sm font-semibold text-gray-700">{t('detail.inEdgesTitle', { count: detail.inEdgeLabels?.length || 0 })}</h3>
            <div className="space-y-1">
              {detail.inEdgeLabels?.map((e) => (
                <div key={e.fullName} className="flex items-center gap-1 text-sm text-gray-600">
                  <GitFork size={13} className="text-pink-400" /> {e.fullName}
                </div>
              )) || <span className="text-xs text-gray-300">{t('common:noData')}</span>}
            </div>
          </div>
          <div>
            <h3 className="mb-2 text-sm font-semibold text-gray-700">{t('detail.outEdgesTitle', { count: detail.outEdgeLabels?.length || 0 })}</h3>
            <div className="space-y-1">
              {detail.outEdgeLabels?.map((e) => (
                <div key={e.fullName} className="flex items-center gap-1 text-sm text-gray-600">
                  <GitFork size={13} className="text-pink-400" /> {e.fullName}
                </div>
              )) || <span className="text-xs text-gray-300">{t('common:noData')}</span>}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function TableSchemaDetail({ columns }) {
  const { t } = useTranslation('vertexType')
  return (
    <div className="overflow-hidden rounded-lg border border-gray-200">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
          <tr>
            <th className="px-3 py-2">{t('detail.colName')}</th>
            <th className="px-3 py-2">{t('detail.dataType')}</th>
            <th className="px-3 py-2 text-center">{t('detail.nullable')}</th>
            <th className="px-3 py-2">{t('detail.defaultValue')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {columns.length === 0 && (
            <tr><td colSpan={4} className="px-3 py-6 text-center text-gray-400">{t('detail.noTable')}</td></tr>
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
  const { t } = useTranslation('vertexType')
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
              {copied === i ? t('common:copied') : t('common:copy')}
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

const inputCls = 'flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'

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
            : 'text-gray-500 hover:bg-indigo-50 hover:text-indigo-600'}
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
