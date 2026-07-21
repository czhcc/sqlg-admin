import { useEffect, useState, useCallback, useRef } from 'react'
import CodeMirror from '@uiw/react-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { EditorView, keymap } from '@codemirror/view'
import { Prec } from '@codemirror/state'
import { gremlinAutocomplete } from '../utils/gremlinAutocomplete'
import {
  listGremlinConnections, setActiveConnection, executeGremlin,
  getHistory, deleteHistory, clearHistory,
  getFavorites, addFavorite, updateFavorite, deleteFavorite,
  refreshConnection,
} from '../api/gremlin'
import {
  Database, Star, ChevronDown, TerminalSquare, Play, Trash2,
  Bookmark, BookmarkPlus, Clock, X, RefreshCw, Eye, Code2,
  Table as TableIcon, Share2, GitBranch, FileJson, AlertTriangle,
  Shield, ShieldCheck, ShieldAlert, CheckCircle2, XCircle, Search,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'

const MODES = [
  { value: 'READONLY', labelKey: 'modeReadonly', icon: ShieldCheck, color: 'text-green-600', descKey: 'modeReadonlyDesc' },
  { value: 'READWRITE', labelKey: 'modeReadwrite', icon: Shield, color: 'text-amber-600', descKey: 'modeReadwriteDesc' },
  { value: 'ADMIN', labelKey: 'modeAdmin', icon: ShieldAlert, color: 'text-red-600', descKey: 'modeAdminDesc' },
]

const RESULT_TABS = [
  { value: 'table', labelKey: 'resultTabTable', icon: TableIcon },
  { value: 'json', labelKey: 'resultTabJson', icon: FileJson },
  { value: 'graph', labelKey: 'resultTabGraph', icon: Share2 },
  { value: 'path', labelKey: 'resultTabPath', icon: GitBranch },
  { value: 'raw', labelKey: 'resultTabRaw', icon: Code2 },
]

const SAMPLE_QUERIES = [
  { title: 'Query all Person (limit 10)', query: "g.V().hasLabel('Person').limit(10).valueMap(true)", mode: 'READONLY' },
  { title: 'Count all vertices', query: "g.V().count()", mode: 'READONLY' },
  { title: 'Query vertex and its neighbors', query: "g.V().hasLabel('Person').limit(5).both().valueMap(true)", mode: 'READONLY' },
  { title: 'Query edge data', query: "g.E().limit(10).valueMap(true)", mode: 'READONLY' },
  { title: 'Path query', query: "g.V().hasLabel('Person').limit(1).out().path()", mode: 'READONLY' },
  { title: 'Query by property', query: "g.V().has('object_id','person_001').valueMap(true)", mode: 'READONLY' },
]

export default function GremlinConsole() {
  const { t, i18n } = useTranslation('gremlin')
  const [connections, setConnections] = useState([])
  const [selectedConnId, setSelectedConnId] = useState(null)
  const [connDropdown, setConnDropdown] = useState(false)
  const [toast, setToast] = useState(null)

  const [query, setQuery] = useState('')
  const [mode, setMode] = useState('READONLY')
  const [executing, setExecuting] = useState(false)
  const [result, setResult] = useState(null)
  const [resultTab, setResultTab] = useState('table')
  const [activeTab, setActiveTab] = useState('editor')
  const [splitRatio, setSplitRatio] = useState(0.55)
  const draggingRef = useRef(false)
  const containerRef = useRef(null)

  const [history, setHistory] = useState([])
  const [favorites, setFavorites] = useState([])

  const editorRef = useRef(null)
  const selectedConnIdRef = useRef(null)
  const onExecuteRef = useRef(() => {})

  useEffect(() => { onExecuteRef.current = onExecute })

  const onSplitterMouseDown = useCallback((e) => {
    e.preventDefault()
    draggingRef.current = true
    document.body.style.cursor = 'row-resize'
    document.body.style.userSelect = 'none'
  }, [])

  useEffect(() => {
    const onMouseMove = (e) => {
      if (!draggingRef.current || !containerRef.current) return
      const rect = containerRef.current.getBoundingClientRect()
      const ratio = (e.clientY - rect.top) / rect.height
      setSplitRatio(Math.min(0.9, Math.max(0.1, ratio)))
    }
    const onMouseUp = () => {
      if (draggingRef.current) {
        draggingRef.current = false
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
      }
    }
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
    return () => {
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
  }, [])

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listGremlinConnections()
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

  const loadHistory = useCallback(async () => {
    if (!selectedConnId) return
    try {
      const res = await getHistory(selectedConnId)
      setHistory(res.data || [])
    } catch (e) { setHistory([]) }
  }, [selectedConnId])
  useEffect(() => { loadHistory() }, [loadHistory])

  const loadFavorites = useCallback(async () => {
    try {
      const res = await getFavorites()
      setFavorites(res.data || [])
    } catch (e) { setFavorites([]) }
  }, [])
  useEffect(() => { loadFavorites() }, [loadFavorites])

  const getSelectedQuery = () => {
    const view = editorRef.current?.view
    if (!view) return query
    const { main } = view.state.selection
    if (main.from !== main.to) {
      return view.state.sliceDoc(main.from, main.to)
    }
    return query
  }

  const onExecute = async () => {
    const q = getSelectedQuery().trim()
    if (!q) { showToast('error', t('msg.queryRequired')); return }
    if (!selectedConnId) { showToast('error', t('msg.connRequired')); return }

    setExecuting(true)
    setResult(null)
    try {
      const res = await executeGremlin(selectedConnId, { query: q, mode })
      setResult(res.data)
      if (res.data.success) {
        showToast('success', t('msg.querySuccess', { count: res.data.resultCount, ms: res.data.costMs }))
      } else {
        showToast('error', res.data.error || t('msg.queryFailed'))
      }
      loadHistory()
    } catch (e) {
      setResult({ success: false, error: e.message, results: [], resultCount: 0, costMs: 0 })
      showToast('error', e.message)
    } finally {
      setExecuting(false)
    }
  }

  const onFormat = () => {
    const q = getSelectedQuery().trim()
    if (!q) return
    let formatted = q
      .replace(/\.\s*/g, '.\n  ')
      .replace(/,\s*/g, ', ')
    formatted = formatted.replace(/\n\s*\n/g, '\n')
    const view = editorRef.current?.view
    if (view) {
      const { main } = view.state.selection
      if (main.from !== main.to) {
        view.dispatch({
          changes: { from: main.from, to: main.to, insert: formatted }
        })
      } else {
        setQuery(formatted)
      }
    } else {
      setQuery(formatted)
    }
    showToast('success', t('msg.formatted'))
  }

  const onUseHistory = (h) => {
    setQuery(h.queryText)
    setMode(h.mode)
    setActiveTab('editor')
  }

  const onUseFavorite = (f) => {
    setQuery(f.queryText)
    setMode(f.mode)
    setActiveTab('editor')
  }

  const onUseSample = (s) => {
    setQuery(s.query)
    setMode(s.mode)
    setActiveTab('editor')
  }

  const onAddFavorite = async () => {
    const q = getSelectedQuery().trim()
    if (!q) { showToast('error', t('msg.titleRequired')); return }
    const title = window.prompt(t('msg.favTitlePrompt'), q.substring(0, 50))
    if (!title) return
    try {
      await addFavorite({ title, queryText: q, mode })
      showToast('success', t('msg.favSuccess'))
      loadFavorites()
    } catch (e) { showToast('error', e.message) }
  }

  const onDeleteFavorite = async (id) => {
    if (!window.confirm(t('msg.confirmDeleteFav'))) return
    try {
      await deleteFavorite(id)
      showToast('success', t('msg.deleted'))
      loadFavorites()
    } catch (e) { showToast('error', e.message) }
  }

  const onDeleteHistory = async (id) => {
    try {
      await deleteHistory(id)
      loadHistory()
    } catch (e) { showToast('error', e.message) }
  }

  const onClearHistory = async () => {
    if (!window.confirm(t('msg.confirmClearHistory'))) return
    try {
      await clearHistory(selectedConnId)
      showToast('success', t('msg.cleared'))
      loadHistory()
    } catch (e) { showToast('error', e.message) }
  }

  const onRefresh = async () => {
    if (!selectedConnId) return
    try {
      await refreshConnection(selectedConnId)
      showToast('success', t('msg.cacheRefreshed'))
    } catch (e) { showToast('error', e.message) }
  }

  const selectedConn = connections.find((c) => c.id === selectedConnId)
  const currentMode = MODES.find((m) => m.value === mode)

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <TerminalSquare size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button onClick={() => setConnDropdown((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-indigo-500" />
                <span className="truncate font-medium">{selectedConn ? selectedConn.name : t('selectConnection')}</span>
                {selectedConn?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {connDropdown && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setConnDropdown(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">{t('noConnection')}</div>}
                  {connections.map((c) => (
                    <button key={c.id} onClick={() => { setSelectedConnId(c.id); setConnDropdown(false); setActiveConnection(c.id).catch(() => {}) }}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-indigo-50 ${c.id === selectedConnId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'}`}>
                      <span className="flex items-center gap-2 truncate"><Database size={14} className="text-gray-400" /><span className="truncate">{c.name}</span></span>
                      <span className="flex items-center gap-1"><span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{c.dbType}</span>{c.isDefault && <Star size={12} className="fill-amber-400 text-amber-400" />}</span>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>

          <div className="flex items-center gap-1 rounded-md border border-gray-300 bg-white px-1 py-0.5">
            {MODES.map((m) => (
              <button key={m.value} onClick={() => setMode(m.value)} title={t(m.descKey)}
                className={`flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors
                  ${mode === m.value ? `${m.color} bg-gray-100` : 'text-gray-400 hover:text-gray-600'}`}>
                <m.icon size={13} /> {t(m.labelKey)}
              </button>
            ))}
          </div>

          <button onClick={onRefresh} disabled={!selectedConnId}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} /> {t('refresh')}
          </button>
        </div>
      </header>

      <div className="flex flex-1 flex-col overflow-hidden">
        <div className="flex items-center gap-1 border-b border-gray-200 bg-gray-50 px-4 pt-2">
          <TabButton active={activeTab === 'editor'} onClick={() => setActiveTab('editor')} icon={Code2} label={t("tabEditor")} />
          <TabButton active={activeTab === 'history'} onClick={() => setActiveTab('history')} icon={Clock} label={t("tabHistory")} badge={history.length} />
          <TabButton active={activeTab === 'favorites'} onClick={() => setActiveTab('favorites')} icon={Bookmark} label={t("tabFavorites")} badge={favorites.length} />
          <TabButton active={activeTab === 'samples'} onClick={() => setActiveTab('samples')} icon={Search} label={t("tabSamples")} />
        </div>

        <div className="flex-1 overflow-hidden">
          {activeTab === 'editor' && (
            <div ref={containerRef} className="flex h-full flex-col overflow-hidden">
              <div style={{ height: `${splitRatio * 100}%` }} className="flex flex-shrink-0 flex-col overflow-hidden">
                <div className="border-b border-gray-100 bg-gray-50 px-4 py-2">
                  <div className="mb-2 flex items-center gap-2 text-xs text-gray-500">
                    <span className="flex items-center gap-1">
                      <currentMode.icon size={13} className={currentMode.color} />
                      <span className={currentMode.color}>{t(currentMode.labelKey)}{t('modeSuffix')}</span>
                    </span>
                    <span>·</span>
                    <span>{t('selectHint')}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <button onClick={onExecute} disabled={executing || !selectedConnId}
                      className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
                      {executing ? <RefreshCw size={15} className="animate-spin" /> : <Play size={15} />}
                      {executing ? t('executing') : t('execute')}
                    </button>
                    <button onClick={onFormat}
                      className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                      <Code2 size={14} /> {t('format')}
                    </button>
                    <button onClick={onAddFavorite}
                      className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                      <BookmarkPlus size={14} /> {t('favorite')}
                    </button>
                    <button onClick={() => setQuery('')}
                      className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
                      <Trash2 size={14} /> {t('clear')}
                    </button>
                  </div>
                </div>
                <div className="flex-1 overflow-auto">
                  <CodeMirror
                    ref={editorRef}
                    value={query}
                    onChange={setQuery}
                    extensions={[
                      javascript(),
                      EditorView.lineWrapping,
                      gremlinAutocomplete(),
                      Prec.highest(keymap.of([{
                        key: 'Mod-Enter',
                        preventDefault: true,
                        stopPropagation: true,
                        run: () => { onExecuteRef.current(); return true },
                      }])),
                    ]}
                    height="100%"
                    className="h-full text-sm"
                    placeholder={t("placeholder")}
                  />
                </div>
              </div>

              <div
                onMouseDown={onSplitterMouseDown}
                className="group flex h-1 flex-shrink-0 cursor-row-resize items-center justify-center bg-gray-200 hover:bg-indigo-300"
              >
                <div className="h-full w-32 rounded-full bg-gray-300 transition-colors group-hover:bg-indigo-400" style={{ height: '3px' }} />
              </div>

              <div style={{ height: `${(1 - splitRatio) * 100}%` }} className="flex flex-shrink-0 flex-col overflow-hidden">
                <ResultPanel result={result} tab={resultTab} setTab={setResultTab} executing={executing} />
              </div>
            </div>
          )}

          {activeTab === 'history' && (
            <div className="h-full overflow-auto">
              <HistoryPanel history={history} onUse={onUseHistory} onDelete={onDeleteHistory} onClear={onClearHistory} />
            </div>
          )}

          {activeTab === 'favorites' && (
            <div className="h-full overflow-auto">
              <FavoritesPanel favorites={favorites} onUse={onUseFavorite} onDelete={onDeleteFavorite} />
            </div>
          )}

          {activeTab === 'samples' && (
            <div className="h-full overflow-auto">
              <SamplesPanel onUse={onUseSample} />
            </div>
          )}
        </div>
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function ResultPanel({ result, tab, setTab, executing }) {
  const { t } = useTranslation('gremlin')
  if (executing && !result) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-gray-400">
        <div className="text-center">
          <RefreshCw size={28} className="mx-auto mb-2 animate-spin text-indigo-400" />
          <p>{t('executing')}</p>
        </div>
      </div>
    )
  }

  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-gray-400">
        <div className="text-center">
          <Eye size={32} className="mx-auto mb-2 text-gray-300" />
          <p>{t('resultPlaceholder')}</p>
        </div>
      </div>
    )
  }

  if (!result.success) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <div className="max-w-2xl rounded-lg border border-red-200 bg-red-50 p-6">
          <div className="mb-3 flex items-center gap-2 text-red-700">
            <XCircle size={20} />
            <span className="font-semibold">{t('queryFailed')}</span>
          </div>
          <pre className="overflow-auto whitespace-pre-wrap text-sm text-red-600">{result.error}</pre>
          <div className="mt-3 text-xs text-gray-400">{t('cost', { ms: result.costMs })}</div>
        </div>
      </div>
    )
  }

  const results = result.results || []
  const hasVertexEdge = results.some((r) => r?.type === 'vertex' || r?.type === 'edge')
  const hasPath = results.some((r) => r?.type === 'path')

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50 px-4 py-2">
        <div className="flex items-center gap-1">
          {RESULT_TABS.map((rt) => {
            const disabled = (rt.value === 'graph' && !hasVertexEdge) || (rt.value === 'path' && !hasPath)
            return (
              <button key={rt.value} onClick={() => !disabled && setTab(rt.value)} disabled={disabled}
                className={`flex items-center gap-1 rounded px-3 py-1 text-xs font-medium transition-colors
                  ${tab === rt.value ? 'bg-indigo-100 text-indigo-700' : disabled ? 'text-gray-300' : 'text-gray-500 hover:bg-gray-100'}`}>
                <rt.icon size={13} /> {t(rt.labelKey)}
              </button>
            )
          })}
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <span className="flex items-center gap-1"><CheckCircle2 size={13} className="text-green-500" /> {t('success')}</span>
          <span>{t('cost', { ms: result.costMs })}</span>
          <span>{t('returned', { count: result.resultCount })}</span>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-4">
        {tab === 'table' && <ResultTable results={results} />}
        {tab === 'json' && <ResultJson results={results} />}
        {tab === 'graph' && <ResultGraph results={results} />}
        {tab === 'path' && <ResultPath results={results} />}
        {tab === 'raw' && <ResultRaw results={results} />}
      </div>
    </div>
  )
}

function ResultTable({ results }) {
  const { t } = useTranslation('gremlin')
  if (results.length === 0) return <Empty text={t("noResultData")} />
  const columns = extractColumns(results)
  return (
    <div className="overflow-hidden rounded-lg border border-gray-200">
      <div className="max-h-full overflow-auto">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
            <tr>
              <th className="px-3 py-2 w-12">#</th>
              {columns.map((col) => (
                <th key={col} className="px-3 py-2">{col}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {results.map((row, i) => (
              <tr key={i} className="hover:bg-gray-50">
                <td className="px-3 py-2 text-xs text-gray-400">{i + 1}</td>
                {columns.map((col) => {
                  const val = getCellValue(row, col)
                  return <td key={col} className="px-3 py-2 text-xs text-gray-700">{formatCell(val)}</td>
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function ResultJson({ results }) {
  return (
    <pre className="overflow-auto rounded-lg bg-gray-900 p-4 text-sm text-green-400">
      <code>{JSON.stringify(results, null, 2)}</code>
    </pre>
  )
}

function ResultGraph({ results }) {
  const containerRef = useRef(null)
  const graphRef = useRef(null)

  useEffect(() => {
    if (!containerRef.current) return
    import('@antv/g6').then(({ Graph }) => {
      const nodes = []
      const edges = []
      const nodeIds = new Set()
      const edgeIds = new Set()
      const palette = ['#5B8FF9', '#5AD8A6', '#F6BD16', '#E86452', '#6DC8EC', '#945FB9', '#FF9845']
      const colorMap = {}

      function getColor(label) {
        if (!colorMap[label]) colorMap[label] = palette[Object.keys(colorMap).length % palette.length]
        return colorMap[label]
      }

      results.forEach((r) => {
        if (r?.type === 'vertex' && !nodeIds.has(r.id)) {
          nodeIds.add(r.id)
          nodes.push({ id: r.id, data: { label: r.label, properties: r.properties }, style: { fill: getColor(r.label) } })
        }
        if (r?.type === 'edge' && !edgeIds.has(r.id)) {
          edgeIds.add(r.id)
          if (!nodeIds.has(r.outVertex)) { nodeIds.add(r.outVertex); nodes.push({ id: r.outVertex, data: { label: '?' } }) }
          if (!nodeIds.has(r.inVertex)) { nodeIds.add(r.inVertex); nodes.push({ id: r.inVertex, data: { label: '?' } }) }
          edges.push({ id: r.id, source: r.outVertex, target: r.inVertex, data: { label: r.label } })
        }
      })

      if (graphRef.current) { graphRef.current.destroy(); graphRef.current = null }
      if (nodes.length === 0) return

      const graph = new Graph({
        container: containerRef.current,
        autoFit: 'view',
        data: { nodes, edges },
        node: {
          style: {
            size: 32,
            labelText: (d) => {
              const props = d.data?.properties || {}
              return props.name || props.object_id || d.data?.label || truncateStr(d.id, 10)
            },
            labelPlacement: 'bottom',
            labelFontSize: 10,
          },
        },
        edge: { style: { endArrow: true, labelText: (d) => d.data?.label || '', labelFontSize: 8 } },
        layout: { type: 'd3-force', manyBody: { strength: -80 }, collide: { radius: 22 } },
        behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
      })
      graphRef.current = graph
      graph.render()
    })
    return () => { if (graphRef.current) { graphRef.current.destroy(); graphRef.current = null } }
  }, [results])

  return <div ref={containerRef} className="h-full min-h-[400px] w-full" />
}

function ResultPath({ results }) {
  const { t } = useTranslation('gremlin')
  const paths = results.filter((r) => r?.type === 'path')
  if (paths.length === 0) return <Empty text={t("noPathData")} />
  return (
    <div className="space-y-3">
      {paths.map((p, i) => (
        <div key={i} className="rounded-lg border border-gray-200 p-3">
          <div className="mb-2 text-xs font-medium text-gray-500">{t('pathLabel', { index: i + 1 })}</div>
          <div className="flex flex-wrap items-center gap-1">
            {(p.objects || []).map((obj, j) => (
              <div key={j} className="flex items-center gap-1">
                {j > 0 && <span className="text-gray-300">→</span>}
                <span className={`rounded px-2 py-0.5 text-xs ${obj?.type === 'vertex' ? 'bg-blue-50 text-blue-700' : obj?.type === 'edge' ? 'bg-pink-50 text-pink-700' : 'bg-gray-50 text-gray-700'}`}>
                  {obj?.label || obj?.type || String(obj).substring(0, 20)}
                </span>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

function ResultRaw({ results }) {
  return (
    <div className="space-y-2">
      {results.map((r, i) => (
        <div key={i} className="rounded-lg border border-gray-200 p-3">
          <div className="mb-1 text-xs font-medium text-gray-400">#{i + 1}</div>
          <pre className="overflow-auto text-xs text-gray-700"><code>{JSON.stringify(r, null, 2)}</code></pre>
        </div>
      ))}
    </div>
  )
}

function HistoryPanel({ history, onUse, onDelete, onClear }) {
  const { t } = useTranslation('gremlin')
  if (history.length === 0) return <Empty text={t("noHistory")} icon={Clock} />
  return (
    <div className="p-4">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm text-gray-500">{t('historyCount', { count: history.length })}</span>
        <button onClick={onClear} className="flex items-center gap-1 rounded-md border border-red-300 px-2 py-1 text-xs text-red-600 hover:bg-red-50">
          <Trash2 size={12} /> {t('clearHistory')}
        </button>
      </div>
      <div className="space-y-2">
        {history.map((h) => (
          <div key={h.id} className="group rounded-lg border border-gray-200 p-3 hover:border-indigo-300">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0 flex-1" onClick={() => onUse(h)}>
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <span className={`rounded px-1.5 py-0.5 font-medium ${h.success ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
                    {h.success ? t('success') : t('queryFailed')}
                  </span>
                  <span className="rounded bg-gray-100 px-1.5 py-0.5">{h.mode}</span>
                  <span>{h.costMs}ms</span>
                  {h.resultCount != null && <span>· {t('returned', { count: h.resultCount })}</span>}
                  <span>· {formatTime(h.createTime, i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN')}</span>
                </div>
                <pre className="mt-1 overflow-hidden whitespace-pre-wrap break-all text-xs text-gray-700"><code>{h.queryText}</code></pre>
                {h.errorMessage && <div className="mt-1 text-xs text-red-400 truncate">{h.errorMessage}</div>}
              </div>
              <div className="flex flex-col gap-1 opacity-0 group-hover:opacity-100">
                <button onClick={() => onUse(h)} title={t("useThisQuery")} className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-indigo-50 hover:text-indigo-600"><Play size={13} /></button>
                <button onClick={() => onDelete(h.id)} title={t("delete")} className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={13} /></button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function FavoritesPanel({ favorites, onUse, onDelete }) {
  const { t } = useTranslation('gremlin')
  if (favorites.length === 0) return <Empty text={t("noFavorites")} icon={Bookmark} />
  return (
    <div className="p-4">
      <div className="mb-3 text-sm text-gray-500">{t('favoritesCount', { count: favorites.length })}</div>
      <div className="space-y-2">
        {favorites.map((f) => (
          <div key={f.id} className="group rounded-lg border border-gray-200 p-3 hover:border-amber-300">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0 flex-1 cursor-pointer" onClick={() => onUse(f)}>
                <div className="flex items-center gap-2">
                  <Star size={14} className="fill-amber-400 text-amber-400" />
                  <span className="font-medium text-gray-800">{f.title}</span>
                  <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{f.mode}</span>
                </div>
                {f.description && <div className="mt-0.5 text-xs text-gray-400">{f.description}</div>}
                <pre className="mt-1 overflow-hidden whitespace-pre-wrap break-all text-xs text-gray-700"><code>{f.queryText}</code></pre>
              </div>
              <div className="flex flex-col gap-1 opacity-0 group-hover:opacity-100">
                <button onClick={() => onUse(f)} title={t("useThisQuery")} className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-amber-50 hover:text-amber-600"><Play size={13} /></button>
                <button onClick={() => onDelete(f.id)} title={t("delete")} className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={13} /></button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function SamplesPanel({ onUse }) {
  const { t } = useTranslation('gremlin')
  return (
    <div className="p-4">
      <div className="mb-3 text-sm text-gray-500">{t('sampleTitle')}</div>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {SAMPLE_QUERIES.map((s, i) => (
          <button key={i} onClick={() => onUse(s)}
            className="rounded-lg border border-gray-200 p-3 text-left hover:border-indigo-300 hover:bg-indigo-50">
            <div className="flex items-center gap-2">
              <Search size={14} className="text-indigo-400" />
              <span className="text-sm font-medium text-gray-700">{s.title}</span>
              <span className="ml-auto rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{s.mode}</span>
            </div>
            <pre className="mt-1 overflow-hidden whitespace-pre-wrap text-xs text-gray-500"><code>{s.query}</code></pre>
          </button>
        ))}
      </div>
    </div>
  )
}

function TabButton({ active, onClick, icon: Icon, label, badge }) {
  return (
    <button onClick={onClick}
      className={`flex items-center gap-1.5 border-b-2 px-4 py-2 text-sm font-medium transition-colors
        ${active ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
      <Icon size={15} /> {label}
      {badge != null && badge > 0 && (
        <span className="ml-1 rounded-full bg-gray-200 px-1.5 py-0.5 text-[10px] text-gray-600">{badge}</span>
      )}
    </button>
  )
}

function Empty({ text, icon: Icon = Eye }) {
  return (
    <div className="flex h-full items-center justify-center text-sm text-gray-400">
      <div className="text-center"><Icon size={32} className="mx-auto mb-2 text-gray-300" />{text}</div>
    </div>
  )
}

function extractColumns(results) {
  const cols = new LinkedHashSet()
  cols.add('type')
  cols.add('id')
  cols.add('label')
  results.forEach((r) => {
    if (r && typeof r === 'object') {
      if (r.type === 'vertex' || r.type === 'edge') {
        Object.keys(r.properties || {}).forEach((k) => cols.add(k))
        if (r.type === 'edge') { cols.add('outVertex'); cols.add('inVertex') }
      } else if (!r.type) {
        Object.keys(r).forEach((k) => { if (k !== 'type') cols.add(k) })
      }
    }
  })
  return [...cols]
}

class LinkedHashSet extends Set { constructor() { super(); this._order = [] } add(v) { if (!this.has(v)) this._order.push(v); return super.add(v) } *[Symbol.iterator]() { yield* this._order } }

function getCellValue(row, col) {
  if (!row || typeof row !== 'object') return null
  if (col === 'type' || col === 'id' || col === 'label') return row[col]
  if (row.type === 'vertex' || row.type === 'edge') {
    if (col === 'outVertex' || col === 'inVertex') return row[col]
    return row.properties?.[col]
  }
  return row[col]
}

function formatCell(val) {
  if (val == null) return ''
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function formatTime(time, locale) {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleString(locale || 'zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function truncateStr(s, max) { return s.length <= max ? s : s.substring(0, max) + '...' }
