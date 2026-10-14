import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Graph } from '@antv/g6'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { listAlgorithmConnections, getAlgorithmScope, runAlgorithm, getAlgorithmTask, getAlgorithmResult, cancelAlgorithm, exportAlgorithm } from '../api/graphAlgorithm'

const defaults = { algorithm: 'DEGREE', schema: 'public', vertexLabels: [], edgeLabels: [], source: '', target: '', directed: true, weighted: false, weightProperty: 'weight', degreeMode: 'TOTAL', componentMode: 'WEAK', topK: 100, damping: 0.85, iterations: 100, tolerance: 0.000001, maxVertices: 5000, maxEdges: 20000, timeoutSeconds: 30, propertyName: '', propertyValue: '' }
const colors = ['#2563eb', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#db2777']

export default function GraphAlgorithm() {
  const { t } = useTranslation('graphAlgorithm')
  const { hasOp } = useAuth()
  const [connections, setConnections] = useState([])
  const [connection, setConnection] = useState('')
  const [scope, setScope] = useState([])
  const [form, setForm] = useState(defaults)
  const [task, setTask] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [layout, setLayout] = useState('circular')
  const [taskId, setTaskId] = useState('')
  const [page, setPage] = useState(1)
  const nodeNames = useMemo(() => new Map((result?.nodes || []).map(n => [n.id, n.name])), [result])
  const graph = useRef(null)
  const container = useRef(null)
  const active = task && ['PENDING', 'RUNNING', 'CANCELLING'].includes(task.status)
  const selected = scope.find(s => s.name === form.schema)
  useEffect(() => setPage(1), [result])
  const set = (key, value) => setForm(prev => ({ ...prev, [key]: value, ...(key === 'algorithm' && value === 'PAGERANK' ? { directed: true } : {}) }))

  useEffect(() => { let alive = true; listAlgorithmConnections().then(r => { if (alive) setConnections(r.data || []) }).catch(e => { if (alive) setError(e.message) }); return () => { alive = false } }, [])
  useEffect(() => {
    let alive = true
    setScope([])
    if (connection) getAlgorithmScope(connection).then(r => { if (alive) { setScope(r.data); setForm(f => ({ ...f, schema: r.data.find(s => s.name === 'public')?.name || r.data[0]?.name || 'public', vertexLabels: [], edgeLabels: [] })) } }).catch(e => { if (alive) setError(e.message) })
    return () => { alive = false }
  }, [connection])

  useEffect(() => {
    if (!task?.id || !active) return
    let alive = true, timer
    const poll = async () => {
      try {
        const r = await getAlgorithmTask(task.id)
        if (!alive) return
        setTask(r.data)
        if (r.data.status === 'SUCCESS') {
          const data = await getAlgorithmResult(task.id)
          if (alive) setResult(data.data)
        } else if (['PENDING', 'RUNNING', 'CANCELLING'].includes(r.data.status)) timer = setTimeout(poll, 1000)
      } catch (e) { if (alive) setError(e.message) }
    }
    timer = setTimeout(poll, 300)
    return () => { alive = false; clearTimeout(timer) }
  }, [task?.id, active])

  useEffect(() => {
    if (!result || !container.current) return
    let disposed = false
    const rows = new Map(result.rows.map(row => [row.id, row]))
    const path = new Set(result.pathNodes || []), edges = new Set(result.pathEdges || [])
    const display = result.algorithm === 'SHORTEST_PATH'
      ? [...result.nodes.filter(n => path.has(n.id)), ...result.nodes.filter(n => !path.has(n.id))].slice(0, 500)
      : [...result.nodes].sort((a, b) => Number(rows.get(b.id)?.value || 0) - Number(rows.get(a.id)?.value || 0)).slice(0, 500)
    const ids = new Set(display.map(n => n.id))
    const max = Math.max(1e-12, ...result.rows.map(r => Number(r.value)))
    const instance = new Graph({ container: container.current, height: 500, autoResize: true, animation: false,
      layout: { type: layout, preventOverlap: true, nodeSize: 44 },
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
      node: { style: { labelText: d => d.data.name, labelPlacement: 'bottom' } },
      edge: { style: { endArrow: result.parameters.directed, stroke: '#94a3b8' } },
      data: { nodes: display.map(n => ({ id: n.id, data: n, style: {
        size: ['DEGREE', 'PAGERANK'].includes(result.algorithm) ? 24 + 30 * Math.sqrt(Number(rows.get(n.id)?.value || 0) / max) : 30,
        fill: result.algorithm === 'COMPONENTS' ? colors[(Number(rows.get(n.id)?.value || 1) - 1) % colors.length] : path.has(n.id) ? '#ea580c' : '#2563eb',
      } })), edges: result.edges.filter(e => ids.has(e.source) && ids.has(e.target)).sort((a, b) => Number(edges.has(b.id)) - Number(edges.has(a.id))).slice(0, 2000).map(e => ({ id: e.id, source: e.source, target: e.target, style: { stroke: edges.has(e.id) ? '#ea580c' : '#94a3b8', lineWidth: edges.has(e.id) ? 4 : 1 } })) },
    })
    graph.current = instance
    instance.render().then(() => { if (!disposed) return instance.fitView() }).catch(e => { if (!disposed) setError(e.message) })
    return () => { disposed = true; graph.current = null; instance.destroy() }
  }, [result, layout])

  const submit = async e => {
    e.preventDefault(); setBusy(true); setError(''); setResult(null)
    try { const r = await runAlgorithm(connection, form); setTask(r.data); setTaskId(String(r.data.id)) } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const reopen = async () => {
    try { setError(''); const r = await getAlgorithmTask(taskId); setTask(r.data); setResult(null); if (r.data.status === 'SUCCESS') setResult((await getAlgorithmResult(taskId)).data) } catch (e) { setError(e.message) }
  }
  const download = async () => {
    try { const blob = await exportAlgorithm(task.id); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `graph-algorithm-${task.id}.json`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) } catch (e) { setError(e.message) }
  }
  const input = (key, numeric = false, extra = {}) => <label className="flex flex-col gap-1 text-sm">{t(key)}<input className="rounded border px-2 py-1.5" type={numeric ? 'number' : 'text'} value={form[key]} onChange={e => set(key, numeric ? Number(e.target.value) : e.target.value)} {...extra} /></label>
  const select = (key, values) => <label className="flex flex-col gap-1 text-sm">{t(key)}<select className="rounded border px-2 py-1.5" value={form[key]} onChange={e => set(key, e.target.value)}>{values.map(v => <option key={v} value={v}>{t(v)}</option>)}</select></label>
  const multi = (key, values) => <label className="flex flex-col gap-1 text-sm">{t(key)}<select multiple className="h-24 rounded border px-2" value={form[key]} onChange={e => set(key, Array.from(e.target.selectedOptions, o => o.value))}>{values.map(v => <option key={v} value={v}>{v}</option>)}</select><span className="text-xs text-slate-500">{t('allTypes')}</span><button type="button" className="text-left text-xs text-blue-600" onClick={() => set(key, [])}>{t('resetTypes')}</button></label>
  return <div className="space-y-4 p-6">
    <h1 className="text-xl font-semibold">{t('title')}</h1><p className="text-sm text-slate-500">{t('scopeHint')}</p>
    {error && <div role="alert" className="rounded bg-red-50 p-3 text-red-700">{error}</div>}
    <form onSubmit={submit} className="space-y-4 rounded-lg border bg-white p-4">
      <fieldset disabled={busy || active} className="grid gap-4 md:grid-cols-4">
        <label className="flex flex-col gap-1 text-sm">{t('connection')}<select required className="rounded border px-2 py-1.5" value={connection} onChange={e => setConnection(e.target.value)}><option value="">{t('choose')}</option>{connections.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
        <label className="flex flex-col gap-1 text-sm">{t('schema')}<select className="rounded border px-2 py-1.5" value={form.schema} onChange={e => setForm(f => ({ ...f, schema: e.target.value, vertexLabels: [], edgeLabels: [] }))}>{scope.map(s => <option key={s.name}>{s.name}</option>)}</select></label>
        {select('algorithm', ['SHORTEST_PATH', 'DEGREE', 'COMPONENTS', 'PAGERANK'])}{input('topK', true, { min: 1, max: 20000 })}
        {multi('vertexLabels', selected?.vertexLabels || [])}{multi('edgeLabels', selected?.edgeLabels || [])}
        {input('propertyName')}{input('propertyValue')}
        {form.algorithm === 'SHORTEST_PATH' && <>{input('source', false, { required: true })}{input('target', false, { required: true })}<label className="flex items-center gap-2"><input type="checkbox" checked={form.directed} onChange={e => set('directed', e.target.checked)} />{t('directed')}</label></>}
        {form.algorithm === 'DEGREE' && select('degreeMode', ['IN', 'OUT', 'TOTAL'])}
        {form.algorithm === 'COMPONENTS' && select('componentMode', ['WEAK', 'STRONG'])}
        {form.algorithm === 'PAGERANK' && <>{input('damping', true, { min: 0.01, max: 0.99, step: 0.01 })}{input('iterations', true, { min: 1, max: 1000 })}{input('tolerance', true, { min: 1e-12, max: 0.1, step: 'any' })}</>}
        {['SHORTEST_PATH', 'PAGERANK'].includes(form.algorithm) && <><label className="flex items-center gap-2"><input type="checkbox" checked={form.weighted} onChange={e => set('weighted', e.target.checked)} />{t('weighted')}</label>{form.weighted && input('weightProperty')}</>}
        {input('maxVertices', true, { min: 1, max: 20000 })}{input('maxEdges', true, { min: 1, max: 100000 })}{input('timeoutSeconds', true, { min: 1, max: 120 })}
      </fieldset>
      <p className="text-xs text-slate-500">{t('semantics')}</p>
      {hasOp('graph_algorithm:execute') && <button disabled={!connection || busy || active} className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-40">{t('run')}</button>}
    </form>
    <div className="flex flex-wrap items-center gap-3 text-sm"><Link className="text-blue-600" to="/task-center">{t('taskCenter')}</Link><input aria-label={t('taskId')} placeholder={t('taskId')} className="rounded border px-2 py-1" value={taskId} onChange={e => setTaskId(e.target.value)} /><button type="button" disabled={active || !taskId} onClick={reopen}>{t('openTask')}</button></div>
    {task && <div className="flex flex-wrap items-center gap-4 rounded border p-3 text-sm"><span>{task.taskNo} · {t(`status.${task.status}`, { defaultValue: task.status })} · {task.progressPercent || 0}%</span><span>{task.errorMessage}</span>{active && hasOp('graph_algorithm:execute') && <button className="text-red-600" onClick={() => cancelAlgorithm(task.id).catch(e => setError(e.message))}>{t('cancel')}</button>}</div>}
    {result && <>
      <div className="space-y-2 rounded border p-4 text-sm"><div>{t(result.algorithm)} · {t('counts', { vertices: result.vertexCount, edges: result.edgeCount })} · {result.costMs} ms</div><div>{t('readTime')}: {result.readStarted} — {result.readEnded}</div><div>{t('snapshotHint')}</div><details><summary>{t('parameters')}</summary><pre className="overflow-auto">{JSON.stringify(result.parameters, null, 2)}</pre></details>
        {result.algorithm === 'SHORTEST_PATH' && <><p>{result.found ? t('pathSummary', { hops: result.hops, cost: result.cost }) : t('noPath')}</p><p className="break-all">{t('pathNodes')}: {result.pathNodes.join(' → ')}</p><p className="break-all">{t('pathEdges')}: {result.pathEdges.join(' → ')}</p></>}
        {result.algorithm === 'COMPONENTS' && <p>{t('componentCount', { count: result.componentCount })}</p>}
        {result.algorithm === 'PAGERANK' && <p>{t(result.converged ? 'converged' : 'notConverged')} · {t('iterations')}: {result.iterations} · {t('residual')}: {result.residual}</p>}
        {hasOp('graph_algorithm:export') && <button className="text-blue-600" onClick={download}>{t('export')}</button>}
      </div>
      <div className="flex items-center gap-4 text-sm"><span>{t('canvasHint')}</span><select aria-label={t('layout')} value={layout} onChange={e => setLayout(e.target.value)}>{['circular', 'grid', 'force'].map(v => <option key={v} value={v}>{t(v)}</option>)}</select><button onClick={() => graph.current?.fitView()}>{t('fit')}</button></div><div ref={container} className="h-[500px] rounded border bg-white" />
      <div className="overflow-auto rounded border"><table className="w-full text-left text-sm"><thead><tr>{['rank', 'id', 'name', 'value', 'size', 'locate'].map(k => <th key={k} className="p-2">{t(k)}</th>)}</tr></thead><tbody>{result.topRows.slice((page - 1) * 50, page * 50).map((row, i) => <tr key={row.id} className="border-t"><td className="p-2">{(page - 1) * 50 + i + 1}</td><td className="p-2">{row.id}</td><td className="p-2">{nodeNames.get(row.id)}</td><td className="p-2">{row.value}</td><td className="p-2">{row.size ?? '—'}</td><td className="p-2"><button className="text-blue-600" onClick={() => { const g = graph.current; if (g?.getNodeData().some(n => n.id === row.id)) g.focusElement(row.id); else setError(t('outsideCanvas')) }}>{t('locate')}</button></td></tr>)}</tbody></table></div><div className="flex items-center gap-4 text-sm"><button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>{t('previous')}</button><span>{page} / {Math.max(1, Math.ceil(result.topRows.length / 50))}</span><button disabled={page * 50 >= result.topRows.length} onClick={() => setPage(p => p + 1)}>{t('next')}</button></div>
    </>}
  </div>
}
