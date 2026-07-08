import { useEffect, useState, useCallback } from 'react'
import {
  listVertexDataConnections, setActiveConnection, getTree, refreshTree,
  getLabelProperties, pageVertices, getVertexDetail,
  createVertex, updateVertex, deleteVertex, batchDeleteVertices,
  clearVertices, getGremlinExamples, exportVertices,
} from '../api/vertexData'
import {
  Database, Star, ChevronDown, ChevronRight, RefreshCw, Search,
  CircleDot, Boxes, Table, Plus, Pencil, Trash2, Eraser, X,
  Eye, TerminalSquare, Key, ChevronLeft, Download,
} from 'lucide-react'

export default function VertexData() {
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

  // ==================== 连接加载 ====================
  const loadConnections = useCallback(async () => {
    try {
      const res = await listVertexDataConnections()
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

  // ==================== 树加载 ====================
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

  // ==================== 数据加载 ====================
  const loadData = useCallback(async () => {
    if (!selectedLabel || !selectedId) { setRows([]); return }
    setLoading(true)
    try {
      const params = { page, size, ...filters }
      const res = await pageVertices(selectedId, selectedLabel.schema, selectedLabel.label, params)
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
    try { await refreshTree(selectedId); await loadTree(selectedId); if (selectedLabel) await loadData(); showToast('success', '已刷新') }
    catch (e) { showToast('error', e.message) }
  }

  const totalPages = Math.ceil(total / size) || 1

  // ==================== 树搜索 ====================
  const filteredSchemas = (() => {
    if (!tree?.schemas) return []
    if (!treeSearch.trim()) return tree.schemas
    const kw = treeSearch.toLowerCase()
    return tree.schemas.map((s) => ({
      ...s,
      vertexLabels: (s.vertexLabels || []).filter((l) => l.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw)),
    })).filter((s) => s.name.toLowerCase().includes(kw) || (s.vertexLabels || []).length > 0)
  })()

  const onLabelClick = (schema, label) => {
    setSelectedLabel({ schema, label })
    setPage(1)
    setFilters({})
  }

  // ==================== 行操作 ====================
  const onViewDetail = async (row) => {
    try {
      const res = await getVertexDetail(selectedId, row.id)
      setDetailModal({ type: 'detail', title: `点详情 ${row.label} #${row.id}`, data: res.data })
    } catch (e) { showToast('error', e.message) }
  }

  const onViewGremlin = async () => {
    if (!selectedLabel) return
    try {
      const res = await getGremlinExamples(selectedLabel.schema, selectedLabel.label)
      setDetailModal({ type: 'examples', title: `${selectedLabel.label} — Gremlin 示例`, data: res.data, lang: 'gremlin' })
    } catch (e) { showToast('error', e.message) }
  }

  const openCreate = async () => {
    if (!selectedLabel) { showToast('error', '请先选择 VertexLabel'); return }
    try {
      const res = await getLabelProperties(selectedId, selectedLabel.schema, selectedLabel.label)
      setFormModal({ mode: 'create', properties: res.data || [], form: {} })
    } catch (e) { showToast('error', e.message) }
  }

  const openEdit = async (row) => {
    try {
      const [propRes, detailRes] = await Promise.all([
        getLabelProperties(selectedId, selectedLabel.schema, selectedLabel.label),
        getVertexDetail(selectedId, row.id),
      ])
      const existing = detailRes.data?.properties || {}
      setFormModal({
        mode: 'edit',
        vertexId: row.id,
        properties: propRes.data || [],
        form: { ...existing },
      })
    } catch (e) { showToast('error', e.message) }
  }

  const submitForm = async () => {
    if (!formModal || !selectedLabel) return
    setSaving(true)
    try {
      if (formModal.mode === 'create') {
        await createVertex(selectedId, {
          schema: selectedLabel.schema,
          label: selectedLabel.label,
          properties: formModal.form,
        })
        showToast('success', '点创建成功')
      } else {
        await updateVertex(selectedId, formModal.vertexId, {
          schema: selectedLabel.schema,
          label: selectedLabel.label,
          properties: formModal.form,
        })
        showToast('success', '点更新成功')
      }
      setFormModal(null)
      loadData()
    } catch (e) { showToast('error', e.message) }
    finally { setSaving(false) }
  }

  const onDelete = async (row) => {
    if (!window.confirm(`确认删除点「${row.label} #${row.id}」?\n\n删除点会同时删除与该点相关的边数据。`)) return
    try { await deleteVertex(selectedId, row.id); showToast('success', '已删除'); loadData() }
    catch (e) { showToast('error', e.message) }
  }

  const onBatchDelete = async () => {
    if (selectedIds.size === 0) { showToast('error', '请先勾选要删除的点'); return }
    if (!window.confirm(`确认批量删除 ${selectedIds.size} 个点?\n\n此操作不可恢复!`)) return
    try {
      const res = await batchDeleteVertices(selectedId, [...selectedIds])
      showToast('success', `已删除 ${res.data?.deleted || 0} 个点`)
      loadData()
    } catch (e) { showToast('error', e.message) }
  }

  const onClear = async () => {
    if (!selectedLabel) return
    const input = window.prompt(
      `确认清空「${selectedLabel.schema}.${selectedLabel.label}」下的全部点数据?\n\n` +
      `该操作只删除点数据,不会删除点类型和底层表。\n\n` +
      `请输入 label 名称「${selectedLabel.label}」以确认:`
    )
    if (input !== selectedLabel.label) { if (input !== null) showToast('error', '输入不匹配'); return }
    try {
      const res = await clearVertices(selectedId, selectedLabel.schema, selectedLabel.label)
      showToast('success', `已清空 ${res.data?.deleted || 0} 个点`)
      loadData()
    } catch (e) { showToast('error', e.message) }
  }

  const [exportPopover, setExportPopover] = useState(false)
  const [exporting, setExporting] = useState(false)

  const onExport = async (format) => {
    setExportPopover(false)
    setExporting(true)
    try {
      const res = await exportVertices(selectedId, selectedLabel.schema, selectedLabel.label, format, filters)
      let blob
      if (res.data.binary) {
        // Excel xlsx 是二进制,后端 Base64 编码返回,前端解码
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
      showToast('success', `已导出 ${res.data.rowCount} 条 (${format.toUpperCase()})`)
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
    ? Object.keys(rows[0].properties || {}).slice(0, 5)
    : []

  return (
    <div className="flex h-full flex-col">
      {/* ==================== 顶部栏 ==================== */}
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Table size={20} className="text-blue-500" /> 点数据管理
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">查询、浏览和维护 Vertex 实例数据</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-blue-500" />
                <span className="truncate font-medium">{selected ? selected.name : '选择连接'}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">暂无可用连接</div>}
                  {connections.map((c) => (
                    <button key={c.id} onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-blue-50 ${c.id === selectedId ? 'bg-blue-50 text-blue-700' : 'text-gray-700'}`}>
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
            <RefreshCw size={15} className={treeLoading ? 'animate-spin' : ''} /> 刷新
          </button>
        </div>
      </header>

      {/* ==================== 左右布局 ==================== */}
      <div className="flex flex-1 overflow-hidden">
        {/* 左侧树 */}
        <aside className="flex w-64 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
          <div className="border-b border-gray-100 p-3">
            <div className="relative">
              <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
              <input value={treeSearch} onChange={(e) => setTreeSearch(e.target.value)}
                placeholder="过滤 Schema / Label..."
                className="w-full rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-blue-500" />
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {!selectedId && <div className="flex h-full items-center justify-center text-sm text-gray-400">请选择连接</div>}
            {selectedId && treeLoading && !tree && <div className="flex h-full items-center justify-center text-sm text-gray-400">加载中...</div>}
            {filteredSchemas.map((schema) => (
              <div key={schema.name} className="mb-1">
                <div className="flex items-center gap-1.5 rounded px-2 py-1 text-sm">
                  <ChevronRight size={14} className="text-gray-400" />
                  <Boxes size={15} className="text-indigo-500" />
                  <span className="font-semibold text-gray-700">{schema.name}</span>
                </div>
                <div className="ml-3 border-l border-gray-100 pl-2">
                  <div className="px-2 py-0.5 text-xs font-medium uppercase tracking-wider text-gray-400">
                    VertexLabels ({(schema.vertexLabels || []).length})
                  </div>
                  {(schema.vertexLabels || []).map((label) => {
                    const isSel = selectedLabel && selectedLabel.schema === schema.name && selectedLabel.label === label
                    return (
                      <button key={label} onClick={() => onLabelClick(schema.name, label)}
                        className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-sm transition-colors ${isSel ? 'bg-blue-100 font-medium text-blue-700' : 'text-gray-600 hover:bg-gray-50'}`}>
                        <CircleDot size={13} className={isSel ? 'text-blue-500' : 'text-gray-400'} />
                        <span className="truncate">{label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </aside>

        {/* 右侧数据 */}
        <main className="flex-1 overflow-auto bg-gray-50 p-6">
          {!selectedLabel ? (
            <div className="flex h-full items-center justify-center text-sm text-gray-400">
              请从左侧选择一个 VertexLabel
            </div>
          ) : (
            <div className="flex h-full flex-col">
              {/* 工具栏 */}
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50">
                    <CircleDot size={20} className="text-blue-500" />
                  </div>
                  <div>
                    <div className="text-sm text-gray-400">当前对象</div>
                    <div className="text-base font-semibold text-gray-800">
                      VertexLabel <span className="text-blue-600">{selectedLabel.schema}.{selectedLabel.label}</span>
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {selectedIds.size > 0 && (
                    <button onClick={onBatchDelete}
                      className="flex items-center gap-1 rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50">
                      <Trash2 size={14} /> 批量删除 ({selectedIds.size})
                    </button>
                  )}
                  <div className="relative">
                    <button onClick={() => setExportPopover((v) => !v)} disabled={exporting}
                      className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
                      <Download size={14} /> {exporting ? '导出中...' : '导出'}
                    </button>
                    {exportPopover && (
                      <>
                        <div className="fixed inset-0 z-10" onClick={() => setExportPopover(false)} />
                        <div className="absolute right-0 z-20 mt-1 w-36 rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                          <div className="px-3 py-1 text-xs text-gray-400">选择导出格式</div>
                          {[
                            { fmt: 'csv', label: 'CSV', desc: '逗号分隔' },
                            { fmt: 'json', label: 'JSON', desc: 'JSON 数组' },
                            { fmt: 'excel', label: 'Excel', desc: '.xls 文件' },
                          ].map((opt) => (
                            <button key={opt.fmt} onClick={() => onExport(opt.fmt)}
                              className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-blue-50">
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
                    <TerminalSquare size={14} /> Gremlin 示例
                  </button>
                  <button onClick={onClear}
                    className="flex items-center gap-1 rounded-md border border-amber-300 px-3 py-1.5 text-sm text-amber-600 hover:bg-amber-50">
                    <Eraser size={14} /> 清空数据
                  </button>
                  <button onClick={openCreate}
                    className="flex items-center gap-1 rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700">
                    <Plus size={15} /> 新增点
                  </button>
                </div>
              </div>

              {/* 过滤栏 */}
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <span className="text-xs text-gray-400">过滤:</span>
                {propColumns.map((col) => (
                  <input key={col} value={filters[col] || ''} onChange={(e) => onFilterChange(col, e.target.value)}
                    placeholder={col} onKeyDown={(e) => { if (e.key === 'Enter') { setPage(1); loadData() } }}
                    className="w-32 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-blue-500" />
                ))}
                <button onClick={() => { setPage(1); loadData() }}
                  className="flex items-center gap-1 rounded bg-gray-100 px-2 py-1 text-xs text-gray-600 hover:bg-gray-200">
                  <Search size={12} /> 查询
                </button>
                {(Object.keys(filters).length > 0) && (
                  <button onClick={() => { setFilters({}); setPage(1) }}
                    className="rounded px-2 py-1 text-xs text-gray-400 hover:text-gray-600">
                    清除
                  </button>
                )}
              </div>

              {/* 表格 */}
              <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
                <div className="h-full overflow-auto">
                  <table className="min-w-full divide-y divide-gray-200 text-sm">
                    <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                      <tr>
                        <th className="px-3 py-3 w-10">
                          <input type="checkbox" checked={selectedIds.size > 0 && selectedIds.size === rows.length}
                            onChange={toggleSelectAll} className="accent-blue-600" />
                        </th>
                        <th className="px-3 py-3">ID</th>
                        <th className="px-3 py-3">Label</th>
                        {propColumns.map((col) => <th key={col} className="px-3 py-3">{col}</th>)}
                        <th className="px-3 py-3">属性摘要</th>
                        <th className="px-3 py-3 text-right">操作</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {loading && <tr><td colSpan={propColumns.length + 5} className="px-4 py-12 text-center text-gray-400">加载中...</td></tr>}
                      {!loading && rows.length === 0 && <tr><td colSpan={propColumns.length + 5} className="px-4 py-12 text-center text-gray-400">暂无数据</td></tr>}
                      {!loading && rows.map((row) => (
                        <tr key={row.id} className="hover:bg-gray-50">
                          <td className="px-3 py-3">
                            <input type="checkbox" checked={selectedIds.has(row.id)} onChange={() => toggleSelect(row.id)} className="accent-blue-600" />
                          </td>
                          <td className="px-3 py-3 font-mono text-xs text-gray-600">{truncateId(row.id)}</td>
                          <td className="px-3 py-3"><span className="inline-flex items-center gap-1 font-medium text-gray-800"><CircleDot size={12} className="text-blue-400" />{row.label}</span></td>
                          {propColumns.map((col) => (
                            <td key={col} className="px-3 py-3 text-gray-600">{formatVal(row.properties?.[col])}</td>
                          ))}
                          <td className="px-3 py-3 text-xs text-gray-400">{row.propertySummary}</td>
                          <td className="px-3 py-3">
                            <div className="flex items-center justify-end gap-1">
                              <ActionBtn title="查看详情" onClick={() => onViewDetail(row)}><Eye size={14} /></ActionBtn>
                              <ActionBtn title="编辑" onClick={() => openEdit(row)}><Pencil size={14} /></ActionBtn>
                              <ActionBtn title="删除" danger onClick={() => onDelete(row)}><Trash2 size={14} /></ActionBtn>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* 分页 */}
              <div className="mt-3 flex items-center justify-between text-sm">
                <span className="text-gray-500">共 {total} 条,第 {page}/{totalPages} 页</span>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPage(1)} disabled={page <= 1}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">首页</button>
                  <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
                    className="flex items-center gap-1 rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">
                    <ChevronLeft size={12} /> 上一页
                  </button>
                  <span className="px-2 text-xs text-gray-500">{page} / {totalPages}</span>
                  <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">下一页</button>
                  <button onClick={() => setPage(totalPages)} disabled={page >= totalPages}
                    className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">末页</button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* ==================== Detail Modal ==================== */}
      {detailModal && (
        <DetailModal data={detailModal} onClose={() => setDetailModal(null)} />
      )}

      {/* ==================== Form Modal ==================== */}
      {formModal && (
        <VertexFormModal
          formModal={formModal}
          selectedLabel={selectedLabel}
          saving={saving}
          setFormModal={setFormModal}
          onSubmit={submitForm}
        />
      )}

      {/* ==================== Toast ==================== */}
      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

// ==================== Helper Functions ====================

function truncateId(id) {
  if (!id) return ''
  const s = String(id)
  return s.length > 25 ? s.substring(0, 25) + '...' : s
}

function formatVal(val) {
  if (val == null) return <span className="text-gray-300">—</span>
  const s = String(val)
  if (s.length > 50) return s.substring(0, 50) + '...'
  return s
}

// ==================== Detail Modal ====================

function DetailModal({ data, onClose }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">{data.title}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <div className="max-h-[75vh] overflow-y-auto px-6 py-4">
          {data.type === 'detail' && <VertexDetailView detail={data.data} />}
          {data.type === 'examples' && <ExamplesView examples={data.data} lang={data.lang} />}
        </div>
      </div>
    </div>
  )
}

function VertexDetailView({ detail }) {
  const props = detail.properties || {}
  const propEntries = Object.entries(props)
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-4 text-sm">
        <Info label="ID" value={detail.id} mono />
        <Info label="Schema" value={detail.schema} />
        <Info label="Label" value={detail.label} />
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold text-gray-700">属性 ({propEntries.length})</h3>
        <div className="overflow-hidden rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase text-gray-500">
              <tr><th className="px-3 py-2">属性名</th><th className="px-3 py-2">值</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {propEntries.length === 0 && <tr><td colSpan={2} className="px-3 py-4 text-center text-gray-400">无属性</td></tr>}
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

      {(detail.outEdges?.length > 0 || detail.inEdges?.length > 0) && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <h3 className="mb-2 text-sm font-semibold text-gray-700">出边 ({detail.outEdges?.length || 0})</h3>
            <div className="space-y-1">
              {(detail.outEdges || []).length === 0 && <span className="text-xs text-gray-300">无</span>}
              {detail.outEdges?.map((e) => (
                <div key={e.id} className="flex items-center gap-2 rounded border border-gray-100 px-3 py-1.5 text-sm">
                  <span className="font-medium text-pink-600">{e.label}</span>
                  <span className="text-gray-400">→</span>
                  <span className="text-gray-600">{e.inVertexLabel} #{truncateId(e.inVertexId)}</span>
                </div>
              ))}
            </div>
          </div>
          <div>
            <h3 className="mb-2 text-sm font-semibold text-gray-700">入边 ({detail.inEdges?.length || 0})</h3>
            <div className="space-y-1">
              {(detail.inEdges || []).length === 0 && <span className="text-xs text-gray-300">无</span>}
              {detail.inEdges?.map((e) => (
                <div key={e.id} className="flex items-center gap-2 rounded border border-gray-100 px-3 py-1.5 text-sm">
                  <span className="font-medium text-pink-600">{e.label}</span>
                  <span className="text-gray-400">←</span>
                  <span className="text-gray-600">{e.outVertexLabel} #{truncateId(e.outVertexId)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {detail.adjacentVertices?.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold text-gray-700">邻接点 ({detail.adjacentVertices.length})</h3>
          <div className="flex flex-wrap gap-2">
            {detail.adjacentVertices.map((v) => (
              <span key={v.id} className="inline-flex items-center gap-1 rounded bg-blue-50 px-2 py-1 text-xs text-blue-700">
                <CircleDot size={11} /> {v.label} #{truncateId(v.id)}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function ExamplesView({ examples, lang }) {
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
              {copied === i ? '已复制' : '复制'}
            </button>
          </div>
          <pre className="overflow-x-auto px-3 py-2 text-sm"><code className={lang === 'gremlin' ? 'text-indigo-700' : 'text-blue-700'}>{ex.code}</code></pre>
        </div>
      ))}
    </div>
  )
}

// ==================== Vertex Form Modal ====================

function VertexFormModal({ formModal, selectedLabel, saving, setFormModal, onSubmit }) {
  const { mode, properties, form } = formModal
  const setField = (name, val) => {
    formModal.form[name] = val
  }
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-800">
            {mode === 'create' ? '新增点' : '编辑点'}
          </h2>
          <button onClick={() => setFormModal(null)} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="max-h-[65vh] overflow-y-auto px-6 py-4">
          <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 px-4 py-2.5">
            <span className="text-xs text-gray-400">目标 VertexLabel: </span>
            <span className="text-sm font-medium text-gray-700">{selectedLabel?.schema}.{selectedLabel?.label}</span>
          </div>

          {properties.length === 0 && <div className="py-4 text-center text-sm text-gray-400">该 VertexLabel 没有属性定义</div>}
          <div className="space-y-3">
            {properties.map((prop) => (
              <div key={prop.name}>
                <label className="mb-1 flex items-center gap-1.5 text-sm font-medium text-gray-700">
                  {prop.name}
                  <span className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">{prop.type}</span>
                  {prop.identifier && <span className="inline-flex items-center gap-0.5 rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-700"><Key size={9} />ID</span>}
                </label>
                {prop.type === 'BOOLEAN' ? (
                  <select className={inputCls} defaultValue={form[prop.name] ?? ''}
                    onChange={(e) => setField(prop.name, e.target.value)}>
                    <option value="">—</option>
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                ) : prop.type === 'JSON' || prop.type === 'JSON_ARRAY' ? (
                  <textarea className={`${inputCls} h-24 resize-none font-mono text-xs`}
                    defaultValue={typeof form[prop.name] === 'string' ? form[prop.name] : ''}
                    placeholder='{"key":"value"}'
                    onChange={(e) => setField(prop.name, e.target.value)} />
                ) : (
                  <input className={inputCls} defaultValue={form[prop.name] ?? ''}
                    onChange={(e) => setField(prop.name, e.target.value)} />
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
          <button onClick={() => setFormModal(null)}
            className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">取消</button>
          <button onClick={onSubmit} disabled={saving}
            className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ==================== Shared ====================

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-100'

function ActionBtn({ title, onClick, disabled, danger, children }) {
  return (
    <button title={title} onClick={onClick} disabled={disabled}
      className={`flex h-7 w-7 items-center justify-center rounded transition-colors
        ${danger ? 'text-gray-500 hover:bg-red-50 hover:text-red-600' : 'text-gray-500 hover:bg-blue-50 hover:text-blue-600'}
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
