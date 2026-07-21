import { useEffect, useState, useCallback, useRef } from 'react'
import { Graph as G6Graph } from '@antv/g6'
import {
  listGraphExploreConnections,
  setActiveConnection,
  getSchemas,
  getVertexLabelProperties,
  searchVertices,
  expandNeighbors,
  refreshConnection,
} from '../api/graphExplore'
import {
  Database, Star, ChevronDown, Network, Search, X,
  RefreshCw, Layers, Maximize, Trash2, Zap,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'

const LAYOUTS = [
  { value: 'force', labelKey: 'layoutForce' },
  { value: 'dagre', labelKey: 'layoutDagre' },
  { value: 'radial', labelKey: 'layoutRadial' },
  { value: 'circular', labelKey: 'layoutCircular' },
  { value: 'grid', labelKey: 'layoutGrid' },
]

const PALETTE = [
  '#5B8FF9', '#5AD8A6', '#5D7092', '#F6BD16', '#E86452',
  '#6DC8EC', '#945FB9', '#FF9845', '#1E9493', '#FF99C3',
]

export default function GraphExplore() {
  const { t } = useTranslation('graphExplore')
  const [connections, setConnections] = useState([])
  const [selectedConnId, setSelectedConnId] = useState(null)
  const [connDropdown, setConnDropdown] = useState(false)
  const [schemas, setSchemas] = useState([])
  const [selectedSchema, setSelectedSchema] = useState(null)
  const [schemaDropdown, setSchemaDropdown] = useState(false)
  const [toast, setToast] = useState(null)

  const [queryModal, setQueryModal] = useState(false)
  const [layout, setLayout] = useState('force')
  const [expanding, setExpanding] = useState(null)

  const containerRef = useRef(null)
  const graphRef = useRef(null)
  const nodeColorMapRef = useRef({})
  const colorIndexRef = useRef(0)
  const selectedConnIdRef = useRef(null)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const getColorForLabel = (label) => {
    if (!nodeColorMapRef.current[label]) {
      nodeColorMapRef.current[label] = PALETTE[colorIndexRef.current % PALETTE.length]
      colorIndexRef.current++
    }
    return nodeColorMapRef.current[label]
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listGraphExploreConnections()
      const cl = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(cl)
      if (cl.length > 0) {
        const fallback = cl.find((c) => c.isDefault) || cl[0]
        const initial = remembered != null && cl.some((c) => c.id === remembered) ? remembered : fallback.id
        setSelectedConnId((prev) => (prev == null ? initial : prev))
      }
    } catch (e) { showToast('error', e.message) }
  }, [])
  useEffect(() => { loadConnections() }, [loadConnections])
  useEffect(() => { selectedConnIdRef.current = selectedConnId }, [selectedConnId])

  const loadSchemas = useCallback(async (id) => {
    if (!id) { setSchemas([]); return }
    try {
      const res = await getSchemas(id)
      const sl = res.data || []
      setSchemas(sl)
      const first = sl.find((s) => s.name === 'public') || sl[0]
      setSelectedSchema(first?.name || null)
    } catch (e) { showToast('error', e.message); setSchemas([]) }
  }, [])
  useEffect(() => { loadSchemas(selectedConnId) }, [selectedConnId, loadSchemas])

  const initGraph = useCallback(() => {
    if (!containerRef.current || graphRef.current) return
    const graph = new G6Graph({
      container: containerRef.current,
      autoFit: 'view',
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
      layout: buildLayoutConfig(layout),
      node: {
        style: {
          size: 36,
          fill: (d) => getColorForLabel(d.data?.label || d.id),
          fillOpacity: 0.85,
          stroke: '#fff',
          lineWidth: 2,
          labelText: (d) => {
            const props = d.data?.properties || {}
            const nameKey = ['name', 'object_id', 'title', 'label'].find((k) => props[k])
            return nameKey ? truncateStr(String(props[nameKey]), 12) : truncateStr(d.data?.label || d.id, 12)
          },
          labelPlacement: 'bottom',
          labelFontSize: 11,
          labelFill: '#374151',
          labelBackground: true,
          labelBackgroundFill: '#ffffff',
          labelBackgroundOpacity: 0.85,
          labelBackgroundRadius: 3,
          labelPadding: [2, 4],
        },
        state: {
          highlight: {
            fill: '#FF6A00',
            stroke: '#FF6A00',
            lineWidth: 3,
            shadowColor: '#FF6A00',
            shadowBlur: 15,
          },
          selected: {
            stroke: '#1890FF',
            lineWidth: 3,
          },
        },
      },
      edge: {
        style: {
          stroke: '#C2C8D5',
          lineWidth: 1.5,
          endArrow: true,
          labelText: (d) => d.data?.label || '',
          labelFontSize: 9,
          labelFill: '#9CA3AF',
          labelBackground: true,
          labelBackgroundFill: '#ffffff',
          labelBackgroundOpacity: 0.8,
        },
        state: {
          highlight: { stroke: '#FF6A00', lineWidth: 2 },
        },
      },
    })
    graphRef.current = graph

    graph.on('node:dblclick', async (evt) => {
      const nodeId = evt.target?.id
      if (!nodeId) return
      await handleExpand(nodeId)
    })

    graph.on('node:click', (evt) => {
      const nodeId = evt.target?.id
      if (!nodeId) return
      const graph = graphRef.current
      if (!graph) return
      const allNodeData = graph.getNodeData()
      allNodeData.forEach((n) => {
        if (n.states?.includes('selected')) {
          graph.setElementState(n.id, n.states.filter((s) => s !== 'selected'))
        }
      })
      graph.setElementState(nodeId, ['selected'])
    })

    graph.render()
  }, [layout])

  useEffect(() => {
    initGraph()
    return () => {
      if (graphRef.current) {
        graphRef.current.destroy()
        graphRef.current = null
      }
    }
  }, [initGraph])

  useEffect(() => {
    const graph = graphRef.current
    if (!graph || graph.destroyed) return
    graph.setLayout(buildLayoutConfig(layout))
    graph.layout().then(() => graph.fitView()).catch(() => {})
  }, [layout])

  const handleExpand = async (vertexId) => {
    const connId = selectedConnIdRef.current
    if (!connId || !vertexId) return
    setExpanding(vertexId)
    try {
      const res = await expandNeighbors(connId, {
        vertexId,
        direction: 'BOTH',
      })
      const data = res.data
      if (!data || !data.nodes) return

      const graph = graphRef.current
      if (!graph) return

      const existingNodes = graph.getNodeData()
      const existingIds = new Set(existingNodes.map((n) => n.id))
      const newNodes = []
      const newEdges = []

      data.nodes.forEach((n) => {
        if (!existingIds.has(n.id)) {
          newNodes.push({
            id: n.id,
            data: { label: n.label, properties: n.properties, schema: n.schema },
            states: ['highlight'],
          })
        }
      })

      const existingEdges = graph.getEdgeData()
      const existingEdgeIds = new Set(existingEdges.map((e) => e.id))
      const allNodeIds = new Set([...existingIds, ...newNodes.map((n) => n.id)])
      data.edges.forEach((e) => {
        if (!existingEdgeIds.has(e.id) && allNodeIds.has(e.source) && allNodeIds.has(e.target)) {
          newEdges.push({
            id: e.id,
            source: e.source,
            target: e.target,
            data: { label: e.label, properties: e.properties },
          })
        }
      })

      if (newNodes.length > 0 || newEdges.length > 0) {
        graph.addData({ nodes: newNodes, edges: newEdges })
        graph.layout().then(() => {
          graph.fitView()
          setTimeout(() => {
            clearHighlights()
          }, 3000)
        }).catch(() => {})
      }

      showToast('success', t('msg.expandOk', { nodes: newNodes.length, edges: newEdges.length }))
      if (data.truncated) {
        showToast('error', t('msg.expandTruncated'))
      }
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setExpanding(null)
    }
  }

  const clearHighlights = () => {
    const graph = graphRef.current
    if (!graph) return
    const allNodeData = graph.getNodeData()
    allNodeData.forEach((n) => {
      if (n.states?.includes('highlight')) {
        graph.setElementState(n.id, n.states.filter((s) => s !== 'highlight'))
      }
    })
  }

  const handleAddVerticesToGraph = (vertices) => {
    const graph = graphRef.current
    if (!graph) return

    const existingNodes = graph.getNodeData()
    const existingIds = new Set(existingNodes.map((n) => n.id))
    const newNodes = vertices.map((v) => ({
      id: v.id,
      data: { label: v.label, properties: v.properties, schema: v.schema },
      states: ['highlight'],
    }))

    const isFirstBatch = existingNodes.length === 0
    if (isFirstBatch) {
      graph.setData({ nodes: newNodes, edges: [] })
    } else {
      const filtered = newNodes.filter((n) => !existingIds.has(n.id))
      if (filtered.length === 0) {
        showToast('error', t('msg.alreadyOnGraph'))
        return
      }
      graph.addData({ nodes: filtered, edges: [] })
    }

    graph.layout().then(() => {
      graph.fitView()
      setTimeout(() => clearHighlights(), 3000)
    }).catch(() => {})
  }

  const onClearGraph = () => {
    const graph = graphRef.current
    if (!graph) return
    if (graph.getNodeData().length === 0) return
    if (!window.confirm(t('msg.confirmClearCanvas'))) return
    graph.setData({ nodes: [], edges: [] })
    graph.render()
    nodeColorMapRef.current = {}
    colorIndexRef.current = 0
  }

  const onFitView = () => {
    graphRef.current?.fitView()
  }

  const onRefresh = async () => {
    if (!selectedConnId) return
    try {
      await refreshConnection(selectedConnId)
      await loadSchemas(selectedConnId)
      showToast('success', t('msg.refreshed'))
    } catch (e) { showToast('error', e.message) }
  }

  const selectedConn = connections.find((c) => c.id === selectedConnId)
  const schemaObj = schemas.find((s) => s.name === selectedSchema)

  const graphStats = (() => {
    const graph = graphRef.current
    if (!graph) return { nodes: 0, edges: 0 }
    return {
      nodes: graph.getNodeData().length,
      edges: graph.getEdgeData().length,
    }
  })()

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Network size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Dropdown
            label={selectedConn ? selectedConn.name : t('selectConnection')}
            icon={<Database size={15} className="text-indigo-500" />}
            open={connDropdown}
            onToggle={() => setConnDropdown((v) => !v)}
            onClose={() => setConnDropdown(false)}
            width="w-64"
          >
            {connections.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">{t('noConnection')}</div>}
            {connections.map((c) => (
              <button key={c.id}
                onClick={() => {
                  setSelectedConnId(c.id)
                  setConnDropdown(false)
                  setActiveConnection(c.id).catch(() => {})
                }}
                className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-indigo-50
                  ${c.id === selectedConnId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'}`}
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
          </Dropdown>

          <Dropdown
            label={selectedSchema || t('selectSchema')}
            icon={<Layers size={15} className="text-purple-500" />}
            open={schemaDropdown}
            onToggle={() => setSchemaDropdown((v) => !v)}
            onClose={() => setSchemaDropdown(false)}
            width="w-48"
          >
            {schemas.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">{t('noSchema')}</div>}
            {schemas.map((s) => (
              <button key={s.name}
                onClick={() => { setSelectedSchema(s.name); setSchemaDropdown(false) }}
                className={`flex w-full items-center gap-2 px-3 py-2 text-sm hover:bg-purple-50
                  ${s.name === selectedSchema ? 'bg-purple-50 text-purple-700' : 'text-gray-700'}`}
              >
                <Layers size={14} className="text-gray-400" />
                <span className="truncate font-medium">{s.name}</span>
                <span className="ml-auto rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">
                  {(s.vertexLabels || []).length}
                </span>
              </button>
            ))}
          </Dropdown>

          <button onClick={onRefresh} disabled={!selectedConnId}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} /> {t('refresh')}
          </button>

          <button onClick={() => setQueryModal(true)} disabled={!selectedConnId || !selectedSchema}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <Search size={15} /> {t('vertexQuery')}
          </button>
        </div>
      </header>

      <div className="flex items-center gap-3 border-b border-gray-100 bg-gray-50 px-6 py-2">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-400">{t('layout')}:</span>
          <select value={layout} onChange={(e) => setLayout(e.target.value)}
            className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs text-gray-700 outline-none focus:border-indigo-500">
            {LAYOUTS.map((l) => <option key={l.value} value={l.value}>{t(l.labelKey)}</option>)}
          </select>
        </div>
        <div className="h-4 w-px bg-gray-300" />
        <button onClick={onFitView}
          className="flex items-center gap-1 rounded-md border border-gray-300 bg-white px-2 py-1 text-xs text-gray-600 hover:bg-gray-50">
          <Maximize size={13} /> {t('fitCanvas')}
        </button>
        <button onClick={onClearGraph}
          className="flex items-center gap-1 rounded-md border border-red-300 bg-white px-2 py-1 text-xs text-red-600 hover:bg-red-50">
          <Trash2 size={13} /> {t('clearCanvas')}
        </button>
        <div className="ml-auto flex items-center gap-3 text-xs text-gray-500">
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-indigo-500" /> {t('nodeCount', { count: graphStats.nodes })}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-pink-400" /> {t('edgeCount', { count: graphStats.edges })}
          </span>
        </div>
      </div>

      <div className="relative flex-1 overflow-hidden bg-gray-50">
        <div ref={containerRef} className="h-full w-full" />
        {!selectedConnId && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
            {t('pleaseSelectConn')}
          </div>
        )}
        {selectedConnId && graphStats.nodes === 0 && !expanding && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
            <div className="rounded-lg bg-white/90 px-6 py-4 text-center shadow-md">
              <Network size={32} className="mx-auto mb-2 text-gray-300" />
              <p className="text-sm text-gray-400">{t('clickToQuery')}</p>
              <p className="text-xs text-gray-400">{t('clickToQueryHint')}</p>
            </div>
          </div>
        )}
        {expanding && (
          <div className="absolute right-4 top-4 flex items-center gap-2 rounded-lg bg-white/95 px-4 py-2 shadow-lg">
            <RefreshCw size={14} className="animate-spin text-indigo-500" />
            <span className="text-xs text-gray-600">{t('expanding')}</span>
          </div>
        )}
        <div className="absolute bottom-4 left-4 flex flex-col gap-1">
          {Object.entries(nodeColorMapRef.current).slice(0, 8).map(([label, color]) => (
            <div key={label} className="flex items-center gap-1.5 rounded bg-white/90 px-2 py-0.5 text-xs shadow-sm">
              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: color }} />
              <span className="text-gray-600">{label}</span>
            </div>
          ))}
        </div>
      </div>

      {queryModal && (
        <VertexQueryModal
          connectionId={selectedConnId}
          schema={selectedSchema}
          vertexLabels={schemaObj?.vertexLabels || []}
          onClose={() => setQueryModal(false)}
          onAddToGraph={(vertices) => {
            handleAddVerticesToGraph(vertices)
            setQueryModal(false)
          }}
        />
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg
          ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function VertexQueryModal({ connectionId, schema, vertexLabels, onClose, onAddToGraph }) {
  const { t } = useTranslation('graphExplore')
  const [selectedLabel, setSelectedLabel] = useState('')
  const [properties, setProperties] = useState([])
  const [propName, setPropName] = useState('')
  const [propValue, setPropValue] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const [selectedIds, setSelectedIds] = useState(new Set())

  const loadProperties = useCallback(async (label) => {
    if (!label) { setProperties([]); return }
    try {
      const res = await getVertexLabelProperties(connectionId, schema, label)
      setProperties(res.data || [])
    } catch (e) { setProperties([]) }
  }, [connectionId, schema])

  const onLabelChange = (label) => {
    setSelectedLabel(label)
    setPropName('')
    setPropValue('')
    setResults([])
    setSearched(false)
    setSelectedIds(new Set())
    loadProperties(label)
  }

  const doSearch = async () => {
    if (!selectedLabel) return
    setLoading(true)
    setSearched(true)
    try {
      const res = await searchVertices(connectionId, schema, selectedLabel, {
        propertyName: propName || undefined,
        propertyValue: propValue || undefined,
      })
      setResults(res.data || [])
      setSelectedIds(new Set())
    } catch (e) {
      setResults([])
    } finally {
      setLoading(false)
    }
  }

  const toggleSelect = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleSelectAll = () => {
    if (selectedIds.size === results.length) setSelectedIds(new Set())
    else setSelectedIds(new Set(results.map((r) => r.id)))
  }

  const onAddGraph = () => {
    const chosen = results.filter((r) => selectedIds.has(r.id))
    if (chosen.length === 0) return
    onAddToGraph(chosen)
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="flex items-center gap-2 text-base font-semibold text-gray-800">
            <Search size={18} className="text-indigo-500" /> {t('vertexQuery')}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="border-b border-gray-100 px-6 py-4">
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">VertexLabel</label>
              <select value={selectedLabel} onChange={(e) => onLabelChange(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500">
                <option value="">{t('selectVertexType')}</option>
                {vertexLabels.map((vl) => (
                  <option key={vl.name} value={vl.name}>{vl.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">{t('queryField')}</label>
              <select value={propName} onChange={(e) => setPropName(e.target.value)}
                disabled={!selectedLabel}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 disabled:bg-gray-100">
                <option value="">{t('allProps')}</option>
                {properties.map((p) => (
                  <option key={p.name} value={p.name}>{p.name} ({p.type})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">{t('queryValue')}</label>
              <div className="flex gap-1">
                <input value={propValue} onChange={(e) => setPropValue(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') doSearch() }}
                  disabled={!selectedLabel}
                  placeholder={t("propValuePlaceholder")}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 disabled:bg-gray-100" />
                <button onClick={doSearch} disabled={!selectedLabel || loading}
                  className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
                  {loading ? <RefreshCw size={14} className="animate-spin" /> : <Search size={14} />}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-hidden">
          {loading && (
            <div className="flex h-48 items-center justify-center text-sm text-gray-400">{t('searching')}</div>
          )}
          {!loading && !searched && (
            <div className="flex h-48 items-center justify-center text-sm text-gray-400">
              {t('selectToSearch')}
            </div>
          )}
          {!loading && searched && results.length === 0 && (
            <div className="flex h-48 items-center justify-center text-sm text-gray-400">{t('noResult')}</div>
          )}
          {!loading && results.length > 0 && (
            <div className="h-full overflow-auto">
              <table className="min-w-full divide-y divide-gray-200 text-sm">
                <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                  <tr>
                    <th className="px-4 py-3 w-10">
                      <input type="checkbox"
                        checked={selectedIds.size > 0 && selectedIds.size === results.length}
                        onChange={toggleSelectAll}
                        className="accent-indigo-600" />
                    </th>
                    <th className="px-4 py-3">ID</th>
                    <th className="px-4 py-3">Label</th>
                    <th className="px-4 py-3">{t('propSummary')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {results.map((r) => (
                    <tr key={r.id} className={`hover:bg-gray-50 ${selectedIds.has(r.id) ? 'bg-indigo-50' : ''}`}>
                      <td className="px-4 py-3">
                        <input type="checkbox" checked={selectedIds.has(r.id)}
                          onChange={() => toggleSelect(r.id)} className="accent-indigo-600" />
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-500">{truncateId(r.id)}</td>
                      <td className="px-4 py-3 font-medium text-gray-700">{r.label}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">{r.propertySummary}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between border-t border-gray-200 bg-gray-50 px-6 py-3">
          <span className="text-xs text-gray-500">
            {results.length > 0 && t('resultCount', { count: results.length })}
            {selectedIds.size > 0 && t('selectedCount', { count: selectedIds.size })}
          </span>
          <div className="flex items-center gap-2">
            <button onClick={onClose}
              className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
              {t('common:cancel')}
            </button>
            <button onClick={onAddGraph} disabled={selectedIds.size === 0}
              className="flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
              <Zap size={14} /> {t('addToGraph', { count: selectedIds.size })}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function Dropdown({ label, icon, open, onToggle, onClose, children, width = 'w-56' }) {
  return (
    <div className="relative">
      <button onClick={onToggle}
        className="flex min-w-[140px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
        <span className="flex items-center gap-2 truncate">
          {icon}
          <span className="truncate font-medium">{label}</span>
        </span>
        <ChevronDown size={15} className="text-gray-400" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={onClose} />
          <div className={`absolute right-0 z-20 mt-1 max-h-72 ${width} overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg`}>
            {children}
          </div>
        </>
      )}
    </div>
  )
}

function truncateId(id) {
  if (!id) return ''
  const s = String(id)
  return s.length > 30 ? s.substring(0, 30) + '...' : s
}

function truncateStr(s, max) {
  return s.length <= max ? s : s.substring(0, max) + '...'
}

function buildLayoutConfig(type) {
  switch (type) {
    case 'force':
      return {
        type: 'd3-force',
        manyBody: { strength: -120 },
        link: { distance: 100, strength: 0.3 },
        collide: { radius: 25, strength: 0.8 },
      }
    case 'dagre':
      return {
        type: 'antv-dagre',
        rankdir: 'LR',
        nodesep: 30,
        ranksep: 80,
      }
    case 'radial':
      return {
        type: 'radial',
        unitRadius: 80,
        linkDistance: 120,
        preventOverlap: true,
        nodeSize: 40,
      }
    case 'circular':
      return {
        type: 'circular',
        radius: 180,
        divisions: 5,
        ordering: 'topology',
      }
    case 'grid':
      return {
        type: 'grid',
        by: 'degree',
        sortLayout: 'arc',
      }
    default:
      return { type: 'force' }
  }
}
