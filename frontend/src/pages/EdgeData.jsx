import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import {
  listEdgeDataConnections, setActiveConnection, getTree, refreshTree,
  getEdgeLabelProperties, pageEdges, getEdgeDetail,
  createEdge, updateEdge, deleteEdge, batchDeleteEdges,
  clearEdges, getEdgeGremlinExamples, exportEdges,
  getEdgeVertexLabels, searchVertices,
} from '../api/edgeData'
import {
  Database, Star, ChevronDown, ChevronRight, RefreshCw, Search,
  Minus, Boxes, GitFork, Plus, Pencil, Trash2, Eraser, X,
  Eye, TerminalSquare, Download, ArrowRight,
} from 'lucide-react'

export default function EdgeData() {
  const { t } = useTranslation('edgeData')
  const { hasOp } = useAuth()
  const [connections, setConnections] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [tree, setTree] = useState(null)
  const [treeLoading, setTreeLoading] = useState(false)
  const [toast, setToast] = useState(null)

  const [treeSearch, setTreeSearch] = useState('')
  const [selectedLabel, setSelectedLabel] = useState(null)

  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [loading, setLoading] = useState(false)
  const [filters, setFilters] = useState({})

  const [selectedIds, setSelectedIds] = useState(new Set())

  const [detailModal, setDetailModal] = useState(null)
  const [formModal, setFormModal] = useState(null)
  const [saving, setSaving] = useState(false)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listEdgeDataConnections()
      const cl = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(cl)
      if (cl.length > 0) {
        const fallback = cl.find((c) => c.isDefault) || cl[0]
        const initial = remembered != null && cl.some((c) => c.id === remembered)
          ? remembered : fallback.id
        setSelectedId((prev) => (prev == null ? initial : prev))
      } else { setSelectedId(null) }
    } catch (e) { showToast('error', e.message) }
  }, [])
  useEffect(() => { loadConnections() }, [loadConnections])

  const loadTree = useCallback(async (id) => {
    if (!id) { setTree(null); return }
    setTreeLoading(true)
    try { const res = await getTree(id); setTree(res.data) }
    catch (e) { showToast('error', e.message); setTree(null) }
    finally { setTreeLoading(false) }
  }, [])
  useEffect(() => {
    loadTree(selectedId)
    setSelectedLabel(null)
    setRows([])
    setTotal(0)
    setPage(1)
    setFilters({})
    setSelectedIds(new Set())
  }, [selectedId, loadTree])

  const loadData = useCallback(async () => {
    if (!selectedLabel || !selectedId) { setRows([]); return }
    setLoading(true)
    try {
      const params = { page, size, ...filters }
      const res = await pageEdges(selectedId, selectedLabel.schema, selectedLabel.label, params)
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
      setSelectedIds(new Set())
    } catch (e) { showToast('error', e.message); setRows([]) }
    finally { setLoading(false) }
  }, [selectedId, selectedLabel, page, size, filters])
  useEffect(() => { loadData() }, [loadData])

  const selected = connections.find((c) => c.id === selectedId)
  const onSelect = async (id) => {
    setSelectedId(id)
    setDropdownOpen(false)
    try { await setActiveConnection(id) } catch (_) {}
  }
  const onRefresh = async () => {
    if (!selectedId) return
    try {
      await refreshTree(selectedId)
      await loadTree(selectedId)
      if (selectedLabel) await loadData()
      showToast('success', t('msg.refreshed'))
    } catch (e) { showToast('error', e.message) }
  }

  const totalPages = Math.ceil(total / size) || 1

  const filteredSchemas = (() => {
    if (!tree?.schemas) return []
    if (!treeSearch.trim()) return tree.schemas
    const kw = treeSearch.toLowerCase()
    return tree.schemas.map((s) => ({
      ...s,
      edgeLabels: (s.edgeLabels || []).filter((l) => l.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw)),
    })).filter((s) => s.name.toLowerCase().includes(kw) || (s.edgeLabels || []).length > 0)
  })()

  const onLabelClick = (schema, label) => {
    setSelectedLabel({ schema, label })
    setPage(1)
    setFilters({})
  }

  const onViewDetail = async (row) => {
    try {
      const res = await getEdgeDetail(selectedId, row.id)
      setDetailModal({ type: 'detail', title: t('edgeDetailTitle', { label: row.label, id: truncateId(row.id) }), data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewGremlin = async () => {
    if (!selectedLabel) return
    try {
      const res = await getEdgeGremlinExamples(selectedLabel.schema, selectedLabel.label)
      setDetailModal({ type: 'examples', title: t('gremlinTitle', { label: selectedLabel.label }), data: res.data, lang: 'gremlin' })
    } catch (e) { showToast('error', e.message) }
  }

  const openCreate = async () => {
    if (!selectedLabel) { showToast('error', t('msg.selectEdgeFirst')); return }
    try {
      const [propRes, vlRes] = await Promise.all([
        getEdgeLabelProperties(selectedId, selectedLabel.schema, selectedLabel.label),
        getEdgeVertexLabels(selectedId, selectedLabel.schema, selectedLabel.label),
      ])
      const outVls = vlRes.data?.outVertexLabels || []
      const inVls = vlRes.data?.inVertexLabels || []
      setFormModal({
        mode: 'create',
        properties: propRes.data || [],
        outVertexLabels: outVls,
        inVertexLabels: inVls,
        form: { outVertexId: '', inVertexId: '', properties: {} },
      })
    } catch (e) { showToast('error', e.message) }
  }

  const openEdit = async (row) => {
    try {
      const [propRes, detailRes] = await Promise.all([
        getEdgeLabelProperties(selectedId, selectedLabel.schema, selectedLabel.label),
        getEdgeDetail(selectedId, row.id),
      ])
      const existing = detailRes.data?.properties || {}
      setFormModal({
        mode: 'edit',
        edgeId: row.id,
        properties: propRes.data || [],
        form: { outVertexId: row.outVertexId, inVertexId: row.inVertexId, properties: { ...existing } },
      })
    } catch (e) { showToast('error', e.message) }
  }

  const submitForm = async () => {
    if (!formModal || !selectedLabel) return
    setSaving(true)
    try {
      const payload = {
        schema: selectedLabel.schema,
        label: selectedLabel.label,
        outVertexId: formModal.form.outVertexId,
        inVertexId: formModal.form.inVertexId,
        properties: formModal.form.properties,
      }
      if (formModal.mode === 'create') {
        await createEdge(selectedId, payload)
        showToast('success', t('msg.edgeCreated'))
      } else {
        await updateEdge(selectedId, formModal.edgeId, payload)
        showToast('success', t('msg.edgeUpdated'))
      }
      setFormModal(null)
      loadData()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const onDelete = async (row) => {
    if (!window.confirm(t('msg.confirmDeleteEdge', { label: row.label, id: truncateId(row.id) }))) return
    try { await deleteEdge(selectedId, row.id); showToast('success', t('common:deleteSuccess')); loadData() }
    catch (e) { showToast('error', e.message) }
  }

  const onBatchDelete = async () => {
    if (selectedIds.size === 0) { showToast('error', t('msg.selectToDelete')); return }
    if (!window.confirm(t('msg.confirmBatchDelete', { count: selectedIds.size }))) return
    try {
      const res = await batchDeleteEdges(selectedId, [...selectedIds])
      showToast('success', t('msg.batchDeleted', { count: res.data?.deleted || 0 }))
      loadData()
    } catch (e) { showToast('error', e.message) }
  }

  const onClear = async () => {
    if (!selectedLabel) return
    const input = window.prompt(
      t('msg.confirmClear', { schema: selectedLabel.schema, label: selectedLabel.label })
    )
    if (input !== selectedLabel.label) { if (input !== null) showToast('error', t('msg.inputMismatch')); return }
    try {
      const res = await clearEdges(selectedId, selectedLabel.schema, selectedLabel.label)
      showToast('success', t('msg.cleared', { count: res.data?.deleted || 0 }))
      loadData()
    } catch (e) { showToast('error', e.message) }
  }

  const [exportPopover, setExportPopover] = useState(false)
  const [exporting, setExporting] = useState(false)

  const onExport = async (format) => {
    setExportPopover(false)
    setExporting(true)
    try {
      const res = await exportEdges(selectedId, selectedLabel.schema, selectedLabel.label, format, filters)
      let blob
      if (res.data.binary) {
        const binary = atob(res.data.content)
        const bytes = new Uint8Array(binary.length)
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
        blob = new Blob([bytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      } else {
        blob = new Blob([res.data.content], { type: 'text/plain;charset=utf-8' })
      }
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = res.data.filename
      a.click()
      URL.revokeObjectURL(url)
      showToast('success', t('msg.exported', { count: res.data.rowCount, format: format.toUpperCase() }))
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setExporting(false)
    }
  }

  const toggleSelect = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }

  const toggleSelectAll = () => {
    if (selectedIds.size === rows.length) setSelectedIds(new Set())
    else setSelectedIds(new Set(rows.map((r) => r.id)))
  }

  const onFilterChange = (key, val) => {
    setFilters((prev) => {
      const next = { ...prev }
      if (val) next[key] = val; else delete next[key]
      return next
    })
  }

  const propColumns = rows.length > 0
    ? Array.from(new Set(rows.flatMap((r) => Object.keys(r.properties || {})))).slice(0, 4)
    : []

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <GitFork size={20} className="text-pink-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-pink-500" />
                <span className="truncate font-medium">{selected ? selected.name : t('common:selectConnection')}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">{t('common:noConnection')}</div>}
                  {connections.map((c) => (
                    <button key={c.id} onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-pink-50 ${c.id === selectedId ? 'bg-pink-50 text-pink-700' : 'text-gray-700'}`}>
                      <span className="flex items-center gap-2 truncate">
                        <Database size={14} className="text-gray-400" /><span className="truncate">{c.name}</span>
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
          <button onClick={onRefresh} disabled={!selectedId || treeLoading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} className={treeLoading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="flex w-64 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
          <div className="border-b border-gray-100 p-3">
            <div className="relative">
              <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
              <input value={treeSearch} onChange={(e) => setTreeSearch(e.target.value)}
                placeholder={t("filterPlaceholder")}
                className="w-full rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-pink-500" />
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {!selectedId && <div className="flex h-full items-center justify-center text-sm text-gray-400">{t('pleaseSelectConn')}</div>}
            {selectedId && treeLoading && !tree && <div className="flex h-full items-center justify-center text-sm text-gray-400">{t('common:loading')}</div>}
            {filteredSchemas.map((schema) => (
              <div key={schema.name} className="mb-1">
                <div className="flex items-center gap-1.5 rounded px-2 py-1 text-sm">
                  <ChevronRight size={14} className="text-gray-400" />
                  <Boxes size={15} className="text-indigo-500" />
                  <span className="font-semibold text-gray-700">{schema.name}</span>
                </div>
                <div className="ml-3 border-l border-gray-100 pl-2">
                  <div className="px-2 py-0.5 text-xs font-medium uppercase tracking-wider text-gray-400">
                    EdgeLabels ({(schema.edgeLabels || []).length})
                  </div>
                  {(schema.edgeLabels || []).map((label) => {
                    const isSel = selectedLabel && selectedLabel.schema === schema.name && selectedLabel.label === label
                    return (
                      <button key={label} onClick={() => onLabelClick(schema.name, label)}
                        className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-sm transition-colors ${isSel ? 'bg-pink-100 font-medium text-pink-700' : 'text-gray-600 hover:bg-gray-50'}`}>
                        <Minus size={13} className={isSel ? 'text-pink-500' : 'text-gray-400'} />
                        <span className="truncate">{label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </aside>

        <main className="flex-1 overflow-auto bg-gray-50 p-6">
          {!selectedLabel ? (
            <div className="flex h-full items-center justify-center text-sm text-gray-400">
              {t('selectEdgeLabel')}
            </div>
          ) : (
            <div className="flex h-full flex-col">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-pink-50">
                    <Minus size={20} className="text-pink-500" />
                  </div>
                  <div>
                    <div className="text-sm text-gray-400">{t('common:currentObject')}</div>
                    <div className="text-base font-semibold text-gray-800">
                      EdgeLabel <span className="text-pink-600">{selectedLabel.schema}.{selectedLabel.label}</span>
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {selectedIds.size > 0 && hasOp('edge_data:batch_delete') && (
                    <button onClick={onBatchDelete}
                      className="flex items-center gap-1 rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50">
                      <Trash2 size={14} /> {t('common:batchDelete')} ({selectedIds.size})
                    </button>
                  )}
                  <div className="relative">
                    <button onClick={() => setExportPopover((v) => !v)} disabled={exporting}
                      className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
                      <Download size={14} /> {exporting ? t('common:exporting') : t('common:export')}
                    </button>
                    {exportPopover && (
                      <>
                        <div className="fixed inset-0 z-10" onClick={() => setExportPopover(false)} />
                        <div className="absolute right-0 z-20 mt-1 w-36 rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                          <div className="px-3 py-1 text-xs text-gray-400">{t('common:selectExportFormat')}</div>
                          {[
                            { fmt: 'csv', label: 'CSV', desc: t('common:formatCsvDesc') },
                            { fmt: 'json', label: 'JSON', desc: t('common:formatJsonDesc') },
                            { fmt: 'excel', label: 'Excel', desc: t('common:formatExcelDesc') },
                          ].map((opt) => (
                            <button key={opt.fmt} onClick={() => onExport(opt.fmt)}
                              className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-pink-50">
                              <Download size={13} className="text-gray-400" />
                              <div>
                                <div className="font-medium">{opt.label}</div>
                                <div className="text-xs text-gray-400">{opt.desc}</div>
                              </div>
                            </button>
                          ))}
                        </div>
                      </>
                    )}
                  </div>
                  <button onClick={onViewGremlin}
                    className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                    <TerminalSquare size={14} /> {t('common:gremlinExamples')}
                  </button>
                  {hasOp('edge_data:clear') && (
                    <button onClick={onClear}
                      className="flex items-center gap-1 rounded-md border border-amber-300 px-3 py-1.5 text-sm text-amber-600 hover:bg-amber-50">
                      <Eraser size={14} /> {t('common:clearData')}
                    </button>
                  )}
                  {hasOp('edge_data:create') && (
                    <button onClick={openCreate}
                      className="flex items-center gap-1 rounded-md bg-pink-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-pink-700">
                      <Plus size={15} /> {t('common:addEdge')}
                    </button>
                  )}
                </div>
              </div>

              <div className="mb-3 flex flex-wrap items-center gap-2">
                <span className="text-xs text-gray-400">{t('common:filter')}:</span>
                <input value={filters.outVertexLabel || ''} onChange={(e) => onFilterChange('outVertexLabel', e.target.value)}
                  placeholder={t("common:outVertexLabel")} onKeyDown={(e) => { if (e.key === 'Enter') { setPage(1); loadData() } }}
                  className="w-32 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-pink-500" />
                <ArrowRight size={12} className="text-gray-400" />
                <input value={filters.inVertexLabel || ''} onChange={(e) => onFilterChange('inVertexLabel', e.target.value)}
                  placeholder={t("common:inVertexLabel")} onKeyDown={(e) => { if (e.key === 'Enter') { setPage(1); loadData() } }}
                  className="w-32 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-pink-500" />
                <span className="text-gray-300">|</span>
                {propColumns.map((col) => (
                  <input key={col} value={filters[col] || ''} onChange={(e) => onFilterChange(col, e.target.value)}
                    placeholder={col} onKeyDown={(e) => { if (e.key === 'Enter') { setPage(1); loadData() } }}
                    className="w-28 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-pink-500" />
                ))}
                <button onClick={() => { setPage(1); loadData() }}
                  className="flex items-center gap-1 rounded bg-gray-100 px-2 py-1 text-xs text-gray-600 hover:bg-gray-200">
                  <Search size={12} /> {t('common:query')}
                </button>
                {(Object.keys(filters).length > 0) && (
                  <button onClick={() => { setFilters({}); setPage(1) }}
                    className="rounded px-2 py-1 text-xs text-gray-400 hover:text-gray-600">
                    {t('common:clear')}
                  </button>
                )}
              </div>

              <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
                <div className="h-full overflow-auto">
                  <table className="min-w-full divide-y divide-gray-200 text-sm">
                    <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                      <tr>
                        <th className="px-3 py-3 w-10">
                          <input type="checkbox" checked={selectedIds.size > 0 && selectedIds.size === rows.length}
                            onChange={toggleSelectAll} className="accent-pink-600" />
                        </th>
                        <th className="px-3 py-3">ID</th>
                        <th className="px-3 py-3">Label</th>
                        <th className="px-3 py-3">{t('col.outVertex')}</th>
                        <th className="px-3 py-3 w-8"></th>
                        <th className="px-3 py-3">{t('col.inVertex')}</th>
                        <th className="px-3 py-3">{t('col.propSummary')}</th>
                        <th className="px-3 py-3 text-right">{t('common:actions')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {loading && <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>}
                      {!loading && rows.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('common:noData')}</td></tr>}
                      {!loading && rows.map((row) => (
                        <tr key={row.id} className="hover:bg-gray-50">
                          <td className="px-3 py-3">
                            <input type="checkbox" checked={selectedIds.has(row.id)} onChange={() => toggleSelect(row.id)} className="accent-pink-600" />
                          </td>
                          <td className="px-3 py-3 font-mono text-xs text-gray-600">{truncateId(row.id)}</td>
                          <td className="px-3 py-3"><span className="inline-flex items-center gap-1 font-medium text-gray-800"><Minus size={12} className="text-pink-400" />{row.label}</span></td>
                          <td className="px-3 py-3 text-xs text-gray-600">
                            <div className="flex flex-col">
                              <span className="font-medium text-gray-700">{row.outVertexLabel}</span>
                              <span className="font-mono text-[10px] text-gray-400">{truncateId(row.outVertexId)}</span>
                            </div>
                          </td>
                          <td className="px-3 py-3 text-center text-gray-400"><ArrowRight size={14} className="inline" /></td>
                          <td className="px-3 py-3 text-xs text-gray-600">
                            <div className="flex flex-col">
                              <span className="font-medium text-gray-700">{row.inVertexLabel}</span>
                              <span className="font-mono text-[10px] text-gray-400">{truncateId(row.inVertexId)}</span>
                            </div>
                          </td>
                          <td className="px-3 py-3 text-xs text-gray-400">{row.propertySummary}</td>
                          <td className="px-3 py-3">
                            <div className="flex items-center justify-end gap-1">
                              <ActionBtn title={t("common:viewDetail")} onClick={() => onViewDetail(row)}><Eye size={14} /></ActionBtn>
                              {hasOp('edge_data:update') && <ActionBtn title={t("common:edit")} onClick={() => openEdit(row)}><Pencil size={14} /></ActionBtn>}
                              {hasOp('edge_data:delete') && <ActionBtn title={t("common:delete")} danger onClick={() => onDelete(row)}><Trash2 size={14} /></ActionBtn>}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between text-sm">
                <span className="text-gray-500">{t('common:total', { count: total })} · {t('common:pageOf', { current: page, total: totalPages })}</span>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPage(1)} disabled={page <= 1}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">{t('common:firstPage')}</button>
                  <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">{t('common:prevPage')}</button>
                  <span className="px-2 text-xs text-gray-500">{page} / {totalPages}</span>
                  <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">{t('common:nextPage')}</button>
                  <button onClick={() => setPage(totalPages)} disabled={page >= totalPages}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">{t('common:lastPage')}</button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {detailModal && (
        <DetailModal data={detailModal} onClose={() => setDetailModal(null)} />
      )}

      {formModal && (
        <EdgeFormModal
          formModal={formModal}
          selectedLabel={selectedLabel}
          selectedId={selectedId}
          saving={saving}
          setFormModal={setFormModal}
          onSubmit={submitForm}
        />
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function truncateId(id) {
  if (!id) return ''
  const s = String(id)
  return s.length > 25 ? s.substring(0, 25) + '...' : s
}

function DetailModal({ data, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">{data.title}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <div className="max-h-[75vh] overflow-y-auto px-6 py-4">
          {data.type === 'detail' && <EdgeDetailView detail={data.data} />}
          {data.type === 'examples' && <ExamplesView examples={data.data} lang={data.lang} />}
        </div>
      </div>
    </div>
  )
}

function EdgeDetailView({ detail }) {
  const { t } = useTranslation('edgeData')
  const props = detail.properties || {}
  const propEntries = Object.entries(props)
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-4 text-sm">
        <Info label="ID" value={detail.id} mono />
        <Info label="Schema" value={detail.schema} />
        <Info label="Label" value={detail.label} />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <VertexBriefCard title={t("common:outVertexCard")} v={detail.outVertex} color="blue" />
        <VertexBriefCard title={t("common:inVertexCard")} v={detail.inVertex} color="green" />
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-gray-700">{t('common:edgeProps', { count: propEntries.length })}</h3>
        <div className="overflow-hidden rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr><th className="px-3 py-2">Property</th><th className="px-3 py-2">Value</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {propEntries.length === 0 && <tr><td colSpan={2} className="px-3 py-4 text-center text-gray-400">{t('common:noProperties')}</td></tr>}
              {propEntries.map(([k, v]) => (
                <tr key={k}>
                  <td className="px-3 py-2 font-medium text-gray-700">{k}</td>
                  <td className="px-3 py-2 text-gray-600">
                    {isJsonString(v) ? (
                      <pre className="max-h-32 overflow-auto rounded bg-gray-50 p-2 text-xs"><code>{JSON.stringify(JSON.parse(v), null, 2)}</code></pre>
                    ) : (
                      String(v)
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function VertexBriefCard({ title, v, color }) {
  const { t } = useTranslation('edgeData')
  if (!v) return (
    <div className="rounded-lg border border-gray-200 p-3">
      <h4 className="mb-2 text-xs font-semibold uppercase text-gray-400">{title}</h4>
      <div className="text-sm text-gray-400">{t('common:none')}</div>
    </div>
  )
  const props = v.properties || {}
  const colorCls = color === 'blue' ? 'text-blue-700' : 'text-green-700'
  return (
    <div className="rounded-lg border border-gray-200 p-3">
      <h4 className="mb-2 text-xs font-semibold uppercase text-gray-400">{title}</h4>
      <div className="mb-2 flex flex-wrap gap-x-3 gap-y-1 text-xs">
        <span className="text-gray-400">Label:</span><span className={`font-medium ${colorCls}`}>{v.label}</span>
        <span className="text-gray-400">Schema:</span><span className="font-medium text-gray-700">{v.schema}</span>
      </div>
      <div className="mb-1 text-[10px] text-gray-400">ID: <span className="font-mono">{truncateId(v.id)}</span></div>
      {Object.keys(props).length > 0 && (
        <div className="mt-2 space-y-0.5">
          {Object.entries(props).slice(0, 5).map(([k, val]) => (
            <div key={k} className="flex justify-between text-xs">
              <span className="text-gray-500">{k}</span>
              <span className="ml-2 truncate text-gray-700" title={String(val)}>{String(val)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function ExamplesView({ examples, lang }) {
  const { t } = useTranslation('edgeData')
  const [copied, setCopied] = useState(null)
  const copy = (code, idx) => {
    navigator.clipboard.writeText(code).then(() => { setCopied(idx); setTimeout(() => setCopied(null), 1500) })
  }
  return (
    <div className="space-y-2">
      {examples.map((ex, i) => (
        <div key={i} className="rounded-lg border border-gray-200">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
            <span className="text-sm font-medium text-gray-700">{ex.title}</span>
            <button onClick={() => copy(ex.code, i)} className="rounded border border-gray-200 px-2 py-0.5 text-xs text-gray-500 hover:bg-gray-50">
              {copied === i ? t('common:copied') : t('common:copy')}
            </button>
          </div>
          <pre className="overflow-x-auto px-3 py-2 text-sm"><code className={lang === 'gremlin' ? 'text-indigo-700' : 'text-blue-700'}>{ex.code}</code></pre>
        </div>
      ))}
    </div>
  )
}

function EdgeFormModal({ formModal, selectedLabel, selectedId, saving, setFormModal, onSubmit }) {
  const { t } = useTranslation('edgeData')
  const { mode, properties, form, outVertexLabels, inVertexLabels } = formModal
  const setField = (name, val) => {
    form.properties[name] = val
  }
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">
            {mode === 'create' ? t('common:createEdge') : t('common:editEdge')}
          </h2>
          <button onClick={() => setFormModal(null)} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="max-h-[65vh] overflow-y-auto px-6 py-4">
          <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 px-4 py-2.5">
            <span className="text-xs text-gray-400">{t('common:targetEdgeLabel')}: </span>
            <span className="text-sm font-medium text-gray-700">{selectedLabel?.schema}.{selectedLabel?.label}</span>
          </div>

          {mode === 'create' && (
            <div className="mb-4 grid grid-cols-2 gap-3">
              <VertexPicker
                label={t("common:outVertexCard")}
                vertexLabels={outVertexLabels}
                onSelect={(id) => { form.outVertexId = id }}
                connectionId={selectedId}
              />
              <VertexPicker
                label={t("common:inVertexCard")}
                vertexLabels={inVertexLabels}
                onSelect={(id) => { form.inVertexId = id }}
                connectionId={selectedId}
              />
            </div>
          )}

          {mode === 'edit' && (
            <div className="mb-4 grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2">
                <div className="text-xs text-gray-400">{t('edgeForm.outVertexId')}</div>
                <div className="font-mono text-xs text-gray-700">{truncateId(form.outVertexId)}</div>
              </div>
              <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2">
                <div className="text-xs text-gray-400">{t('edgeForm.inVertexId')}</div>
                <div className="font-mono text-xs text-gray-700">{truncateId(form.inVertexId)}</div>
              </div>
            </div>
          )}

          {properties.length === 0 && <div className="py-4 text-center text-sm text-gray-400">{t('edgeForm.noProps', { type: 'EdgeLabel' })}</div>}
          <div className="space-y-3">
            {properties.map((prop) => (
              <div key={prop.name}>
                <label className="mb-1 flex items-center gap-1.5 text-sm font-medium text-gray-700">
                  {prop.name}
                  <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">{prop.type}</span>
                </label>
                {prop.type === 'BOOLEAN' ? (
                  <select className={inputCls} defaultValue={form.properties[prop.name] ?? ''}
                    onChange={(e) => setField(prop.name, e.target.value)}>
                    <option value="">—</option>
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                ) : prop.type === 'JSON' || prop.type === 'JSON_ARRAY' ? (
                  <textarea className={`${inputCls} h-24 resize-none font-mono text-xs`}
                    defaultValue={typeof form.properties[prop.name] === 'string' ? form.properties[prop.name] : ''}
                    placeholder='{"key":"value"}'
                    onChange={(e) => setField(prop.name, e.target.value)} />
                ) : (
                  <input className={inputCls} defaultValue={form.properties[prop.name] ?? ''}
                    onChange={(e) => setField(prop.name, e.target.value)} />
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button onClick={() => setFormModal(null)}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">{t('common:cancel')}</button>
          <button onClick={onSubmit} disabled={saving}
            className="rounded-md bg-pink-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50">
            {saving ? t('common:saving') : t('common:save')}
          </button>
        </div>
      </div>
    </div>
  )
}

function VertexPicker({ label, vertexLabels, onSelect, connectionId }) {
  const { t } = useTranslation('edgeData')
  const [chosenVl, setChosenVl] = useState(null)
  const [search, setSearch] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [picked, setPicked] = useState(null)

  const doSearch = async () => {
    if (!connectionId || !chosenVl) return
    setLoading(true)
    try {
      const res = await searchVertices(connectionId, chosenVl.schema, chosenVl.label, { search, page: 1, size: 20 })
      setResults(res.data?.rows || [])
    } catch (e) {
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="rounded-lg border border-gray-200 p-3">
      <div className="mb-2 text-xs font-semibold text-gray-700">{label}</div>
      <select
        value={chosenVl?.label || ''}
        onChange={(e) => {
          const vl = vertexLabels.find((v) => v.label === e.target.value)
          setChosenVl(vl)
          setResults([])
          setPicked(null)
        }}
        className="mb-2 w-full rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-pink-500"
      >
        <option value="">{t('vertexPicker.selectType')}</option>
        {vertexLabels.map((vl) => (
          <option key={vl.fullName} value={vl.label}>{vl.schema}.{vl.label}</option>
        ))}
      </select>
      <div className="mb-2 flex gap-1">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') doSearch() }}
          placeholder={t('vertexPicker.searchPlaceholder')}
          disabled={!chosenVl}
          className="w-full rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-pink-500 disabled:bg-gray-100"
        />
        <button
          onClick={doSearch}
          disabled={!chosenVl}
          className="rounded bg-gray-100 px-2 py-1 text-xs text-gray-600 hover:bg-gray-200 disabled:opacity-40"
        >
          <Search size={12} />
        </button>
      </div>
      {picked && (
        <div className="mb-2 rounded bg-pink-50 px-2 py-1 text-xs text-pink-700">
          {t('vertexPicker.selected')} <span className="font-medium">{picked.label}</span>
          <span className="ml-1 font-mono text-[10px]">{truncateId(picked.id)}</span>
        </div>
      )}
      <div className="max-h-32 overflow-y-auto">
        {loading && <div className="py-2 text-center text-xs text-gray-400">{t('vertexPicker.searching')}</div>}
        {!loading && chosenVl && results.length === 0 && <div className="py-2 text-center text-xs text-gray-400">{t('vertexPicker.noResult')}</div>}
        {!loading && !chosenVl && <div className="py-2 text-center text-xs text-gray-400">{t('vertexPicker.selectTypeFirst')}</div>}
        {!loading && results.map((r) => (
          <button
            key={r.id}
            onClick={() => { setPicked(r); onSelect(r.id) }}
            className="block w-full rounded px-2 py-1 text-left text-xs text-gray-700 hover:bg-pink-50"
          >
            <span className="font-medium">{r.label}</span>
            <span className="ml-1 text-gray-400">{r.propertySummary}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-pink-500 focus:ring-1 focus:ring-pink-100'

function ActionBtn({ title, onClick, disabled, danger, children }) {
  return (
    <button title={title} onClick={onClick} disabled={disabled}
      className={`flex h-7 w-7 items-center justify-center rounded transition-colors
        ${danger ? 'text-gray-500 hover:bg-red-50 hover:text-red-600' : 'text-gray-500 hover:bg-pink-50 hover:text-pink-600'}
        ${disabled ? 'cursor-not-allowed opacity-40' : ''}`}>
      {children}
    </button>
  )
}

function Info({ label, value, mono }) {
  return (
    <div>
      <span className="text-xs text-gray-400">{label}: </span>
      <span className={`font-medium text-gray-700 ${mono ? 'font-mono text-xs' : ''}`}>{value}</span>
    </div>
  )
}

function isJsonString(str) {
  if (typeof str !== 'string') return false
  const t = str.trim()
  if (!t.startsWith('{') && !t.startsWith('[')) return false
  try { JSON.parse(t); return true } catch { return false }
}
