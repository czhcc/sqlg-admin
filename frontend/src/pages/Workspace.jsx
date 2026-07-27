import { useEffect, useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  listWorkspaceConnections,
  setWorkspaceActiveConnection,
  getWorkspaceOverview,
  getConnectionStatus,
  getWorkspaceAlerts,
} from '../api/workspace'
import EChart from '../components/EChart'
import {
  Database, ChevronDown, Star, RefreshCw,
  CircleDot, Minus, Tags, Key, Table2, GitFork,
  Boxes, AlertTriangle, CheckCircle2, XCircle,
  Activity, Network, TrendingUp, ArrowRight,
  Clock, Zap, AlertCircle, Info,
} from 'lucide-react'

const TOAST_TIMEOUT = 3000

export default function Workspace() {
  const { t } = useTranslation('workspace')
  const navigate = useNavigate()
  const [connections, setConnections] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [overview, setOverview] = useState(null)
  const [connStatuses, setConnStatuses] = useState([])
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), TOAST_TIMEOUT)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listWorkspaceConnections()
      const list = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(list)
      if (list.length > 0) {
        const fallback = list.find((c) => c.isDefault) || list[0]
        const initial = remembered != null && list.some((c) => c.id === remembered)
          ? remembered
          : fallback.id
        setSelectedId((prev) => prev == null ? initial : prev)
      } else {
        setSelectedId(null)
      }
    } catch (e) {
      showToast('error', e.message)
    }
  }, [])

  useEffect(() => { loadConnections() }, [loadConnections])

  const loadAll = useCallback(async (id) => {
    if (!id) {
      setOverview(null); setConnStatuses([]); setAlerts([])
      return
    }
    setLoading(true)
    try {
      const [ovRes, csRes, alRes] = await Promise.all([
        getWorkspaceOverview(id),
        getConnectionStatus(),
        getWorkspaceAlerts(id),
      ])
      setOverview(ovRes.data)
      setConnStatuses(csRes.data || [])
      setAlerts(alRes.data || [])
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadAll(selectedId) }, [selectedId, loadAll])

  const selected = connections.find((c) => c.id === selectedId)

  const onSelect = async (id) => {
    setSelectedId(id)
    setDropdownOpen(false)
    try {
      await setWorkspaceActiveConnection(id)
    } catch (e) {
      showToast('error', t('msg.switchFail', { message: e.message }))
    }
  }

  const onRefresh = async () => {
    await loadAll(selectedId)
    showToast('success', t('msg.refreshed'))
  }

  const connInfo = overview?.connection
  const topo = overview?.topology
  const dataStats = overview?.dataStats

  const topologyChartOption = useMemo(() => {
    if (!topo) return {}
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
        data: [
          { value: topo.vertexLabelCount, name: t('topology.vertexLabelCount'), itemStyle: { color: '#6366f1' } },
          { value: topo.edgeLabelCount, name: t('topology.edgeLabelCount'), itemStyle: { color: '#10b981' } },
          { value: topo.vertexPropertyCount, name: t('topology.vertexPropertyCount'), itemStyle: { color: '#f59e0b' } },
          { value: topo.edgePropertyCount, name: t('topology.edgePropertyCount'), itemStyle: { color: '#ef4444' } },
          { value: topo.indexCount, name: t('topology.indexCount'), itemStyle: { color: '#8b5cf6' } },
        ].filter((d) => d.value > 0),
      }],
    }
  }, [topo, t])

  const vertexChartOption = useMemo(() => {
    const items = overview?.topVertexLabels || []
    if (items.length === 0) return {}
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: 'category',
        data: items.map((i) => i.label),
        axisLabel: { rotate: items.length > 5 ? 30 : 0, fontSize: 11, interval: 0 },
      },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: items.map((i) => i.count),
        itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40,
      }],
    }
  }, [overview])

  const edgeChartOption = useMemo(() => {
    const items = overview?.topEdgeLabels || []
    if (items.length === 0) return {}
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: 'category',
        data: items.map((i) => i.label),
        axisLabel: { rotate: items.length > 5 ? 30 : 0, fontSize: 11, interval: 0 },
      },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: items.map((i) => i.count),
        itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40,
      }],
    }
  }, [overview])

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">{t('title')}</h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          {connections.length > 0 && (
            <div className="relative">
              <button
                onClick={() => setDropdownOpen((v) => !v)}
                className="flex min-w-[220px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
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
                  <div className="absolute right-0 z-20 mt-1 max-h-72 w-full min-w-[220px] overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                    {connections.map((c) => (
                      <button
                        key={c.id}
                        onClick={() => onSelect(c.id)}
                        className={`flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors ${
                          c.id === selectedId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700 hover:bg-gray-50'
                        }`}
                      >
                        <Database size={14} className="text-gray-400" />
                        <span className="flex-1 truncate text-left">{c.name}</span>
                        {c.isDefault && <Star size={12} className="fill-amber-400 text-amber-400" />}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}
          <button
            onClick={onRefresh}
            disabled={loading || !selectedId}
            className="flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
            {t('common:refresh')}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto bg-gray-50 p-6">
        {connections.length === 0 ? (
          <EmptyState message={t('msg.noConnections')} actionText={t('msg.goToConnection')} onAction={() => navigate('/connection')} />
        ) : !overview ? (
          <div className="flex h-64 items-center justify-center">
            {loading ? <RefreshCw size={24} className="animate-spin text-indigo-400" /> : null}
          </div>
        ) : (
          <div className="space-y-6">
            <ConnectionOverviewCard connInfo={connInfo} t={t} />

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
              <TopologyOverviewCard topo={topo} chartOption={topologyChartOption} t={t} />
              <DataOverviewCard dataStats={dataStats} t={t} />
            </div>

            <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
              {overview.topVertexLabels?.length > 0 && (
                <ChartCard title={t('data.topVertexLabels', { n: overview.topVertexLabels.length })}>
                  <EChart option={vertexChartOption} />
                </ChartCard>
              )}
              {overview.topEdgeLabels?.length > 0 && (
                <ChartCard title={t('data.topEdgeLabels', { n: overview.topEdgeLabels.length })}>
                  <EChart option={edgeChartOption} />
                </ChartCard>
              )}
            </div>

            <ConnectionStatusTable statuses={connStatuses} t={t} navigate={navigate} />

            <AlertsCard alerts={alerts} t={t} />
          </div>
        )}
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>
      )}
    </div>
  )
}

function EmptyState({ message, actionText, onAction }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <Database size={48} className="text-gray-300" />
      <p className="mt-4 text-sm text-gray-500">{message}</p>
      {actionText && (
        <button onClick={onAction} className="mt-4 flex items-center gap-1 rounded-md bg-indigo-600 px-4 py-2 text-sm text-white hover:bg-indigo-700">
          {actionText} <ArrowRight size={15} />
        </button>
      )}
    </div>
  )
}

function StatCard({ icon: Icon, label, value, color, children }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3">
      <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${color || 'bg-indigo-50 text-indigo-600'}`}>
        <Icon size={20} />
      </div>
      <div className="min-w-0">
        <p className="text-xs text-gray-500">{label}</p>
        <p className="truncate text-lg font-semibold text-gray-800">{value}</p>
        {children}
      </div>
    </div>
  )
}

function ConnectionOverviewCard({ connInfo, t }) {
  if (!connInfo) return null
  const isOk = connInfo.status === 'ok'
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-base font-semibold text-gray-800">
          <Database size={18} className="text-indigo-500" />
          {t('connection.title')}
        </h2>
        <div className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${
          isOk ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
        }`}>
          {isOk ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
          {isOk ? t('connection.statusOk') : t('connection.statusError')}
        </div>
      </div>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7">
        <KV label={t('connection.name')} value={connInfo.name} />
        <KV label={t('connection.dbType')} value={connInfo.dbType} />
        <KV label={t('connection.jdbcUrl')} value={connInfo.jdbcUrl} mono />
        <KV label={t('connection.environment')} value={connInfo.environment || '—'} />
        <KV label={t('connection.schemaCount')} value={connInfo.schemaCount ?? 0} />
        <KV label={t('connection.responseTime')} value={connInfo.responseTimeMs != null ? connInfo.responseTimeMs + ' ms' : '—'} />
        <KV label={t('connection.lastCheck')} value={connInfo.lastCheckTime || '—'} />
      </div>
    </section>
  )
}

function KV({ label, value, mono }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`truncate text-sm font-medium text-gray-800 ${mono ? 'font-mono text-xs' : ''}`} title={value}>{value ?? '—'}</p>
    </div>
  )
}

function TopologyOverviewCard({ topo, chartOption, t }) {
  if (!topo) return null
  const hasData = topo.vertexLabelCount + topo.edgeLabelCount + topo.vertexPropertyCount + topo.edgePropertyCount + topo.indexCount > 0
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
        <Network size={18} className="text-indigo-500" />
        {t('topology.title')}
      </h2>
      <div className="grid grid-cols-3 gap-3">
        <StatMini icon={Boxes} label={t('topology.schemaCount')} value={topo.schemaCount} color="bg-blue-50 text-blue-600" />
        <StatMini icon={CircleDot} label={t('topology.vertexLabelCount')} value={topo.vertexLabelCount} color="bg-indigo-50 text-indigo-600" />
        <StatMini icon={Minus} label={t('topology.edgeLabelCount')} value={topo.edgeLabelCount} color="bg-emerald-50 text-emerald-600" />
        <StatMini icon={Tags} label={t('topology.vertexPropertyCount')} value={topo.vertexPropertyCount} color="bg-amber-50 text-amber-600" />
        <StatMini icon={Tags} label={t('topology.edgePropertyCount')} value={topo.edgePropertyCount} color="bg-orange-50 text-orange-600" />
        <StatMini icon={Key} label={t('topology.identifierCount')} value={topo.identifierCount} color="bg-purple-50 text-purple-600" />
        <StatMini icon={Table2} label={t('topology.indexCount')} value={topo.indexCount} color="bg-violet-50 text-violet-600" />
        <StatMini icon={CircleDot} label={t('topology.emptyVertexLabel')} value={topo.emptyVertexLabelCount} color="bg-rose-50 text-rose-600" />
        <StatMini icon={Minus} label={t('topology.emptyEdgeLabel')} value={topo.emptyEdgeLabelCount} color="bg-pink-50 text-pink-600" />
      </div>
      {hasData && (
        <div className="mt-4">
          <p className="mb-2 text-xs font-medium text-gray-500">{t('topology.distribution')}</p>
          <div className="h-48">
            <EChart option={chartOption} />
          </div>
        </div>
      )}
    </section>
  )
}

function StatMini({ icon: Icon, label, value, color }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-gray-100 bg-gray-50 p-2.5">
      <div className={`flex h-8 w-8 items-center justify-center rounded-md ${color}`}>
        <Icon size={16} />
      </div>
      <div className="min-w-0">
        <p className="text-[11px] leading-tight text-gray-500">{label}</p>
        <p className="text-base font-bold text-gray-800">{value}</p>
      </div>
    </div>
  )
}

function DataOverviewCard({ dataStats, t }) {
  if (!dataStats) return null
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
        <TrendingUp size={18} className="text-indigo-500" />
        {t('data.title')}
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
        <StatCard icon={CircleDot} label={t('data.totalVertices')} value={dataStats.totalVertices ?? 0} color="bg-indigo-50 text-indigo-600" />
        <StatCard icon={GitFork} label={t('data.totalEdges')} value={dataStats.totalEdges ?? 0} color="bg-emerald-50 text-emerald-600" />
        <StatCard icon={Activity} label={t('data.orphanVertices')} value={dataStats.orphanVertexCount >= 0 ? dataStats.orphanVertexCount : '—'} color="bg-amber-50 text-amber-600" />
      </div>
      <div className="mt-3 space-y-2">
        {dataStats.largestVertexLabel && (
          <div className="flex items-center justify-between rounded-lg bg-indigo-50 px-3 py-2">
            <span className="text-sm text-gray-600">{t('data.largestVertex')}</span>
            <span className="text-sm font-medium text-indigo-700">{dataStats.largestVertexLabel.fullName} ({dataStats.largestVertexLabel.count})</span>
          </div>
        )}
        {dataStats.largestEdgeLabel && (
          <div className="flex items-center justify-between rounded-lg bg-emerald-50 px-3 py-2">
            <span className="text-sm text-gray-600">{t('data.largestEdge')}</span>
            <span className="text-sm font-medium text-emerald-700">{dataStats.largestEdgeLabel.fullName} ({dataStats.largestEdgeLabel.count})</span>
          </div>
        )}
        <div className="grid grid-cols-2 gap-2">
          <div className="flex items-center gap-2 rounded-lg bg-rose-50 px-3 py-2">
            <CircleDot size={14} className="text-rose-500" />
            <span className="text-sm text-gray-600">{t('data.emptyVertexLabel')}</span>
            <span className="ml-auto text-sm font-medium text-rose-700">{dataStats.emptyVertexLabelCount ?? 0}</span>
          </div>
          <div className="flex items-center gap-2 rounded-lg bg-pink-50 px-3 py-2">
            <Minus size={14} className="text-pink-500" />
            <span className="text-sm text-gray-600">{t('data.emptyEdgeLabel')}</span>
            <span className="ml-auto text-sm font-medium text-pink-700">{dataStats.emptyEdgeLabelCount ?? 0}</span>
          </div>
        </div>
      </div>
    </section>
  )
}

function ChartCard({ title, children }) {
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-3 text-base font-semibold text-gray-800">{title}</h2>
      <div className="h-64">{children}</div>
    </section>
  )
}

function ConnectionStatusTable({ statuses, t, navigate }) {
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
        <Activity size={18} className="text-indigo-500" />
        {t('connStatus.title')}
      </h2>
      {statuses.length === 0 ? (
        <p className="py-8 text-center text-sm text-gray-400">{t('common:noData')}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-left text-xs text-gray-500">
                <th className="pb-2 pr-4 font-medium">{t('connStatus.name')}</th>
                <th className="pb-2 pr-4 font-medium">{t('connStatus.environment')}</th>
                <th className="pb-2 pr-4 font-medium">{t('connStatus.status')}</th>
                <th className="pb-2 pr-4 text-right font-medium">{t('connStatus.responseTime')}</th>
                <th className="pb-2 pr-4 font-medium">{t('connStatus.lastCheck')}</th>
                <th className="pb-2 text-right font-medium">{t('common:actions')}</th>
              </tr>
            </thead>
            <tbody>
              {statuses.map((s) => (
                <tr key={s.id} className="border-b border-gray-50 last:border-0">
                  <td className="py-2.5 pr-4 font-medium text-gray-800">{s.name}</td>
                  <td className="py-2.5 pr-4 text-gray-600">{s.environment || '—'}</td>
                  <td className="py-2.5 pr-4">
                    {s.status === 'ok' ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-xs text-green-700">
                        <CheckCircle2 size={12} /> {t('connStatus.statusOk')}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2 py-0.5 text-xs text-red-700" title={s.errorMessage}>
                        <XCircle size={12} /> {t('connStatus.statusError')}
                      </span>
                    )}
                  </td>
                  <td className="py-2.5 pr-4 text-right text-gray-600">
                    {s.responseTimeMs != null ? `${s.responseTimeMs} ms` : '—'}
                  </td>
                  <td className="py-2.5 pr-4 text-gray-500">{s.lastCheckTime || '—'}</td>
                  <td className="py-2.5 text-right">
                    {s.status !== 'ok' && (
                      <button
                        onClick={() => navigate('/connection')}
                        className="text-xs text-indigo-600 hover:text-indigo-800"
                      >
                        {t('connStatus.goToManage')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function AlertsCard({ alerts, t }) {
  const iconFor = (level) => {
    if (level === 'error') return <XCircle size={18} className="text-red-500" />
    if (level === 'warning') return <AlertTriangle size={18} className="text-amber-500" />
    return <Info size={18} className="text-blue-500" />
  }
  const bgFor = (level) => {
    if (level === 'error') return 'border-l-red-500 bg-red-50'
    if (level === 'warning') return 'border-l-amber-500 bg-amber-50'
    return 'border-l-blue-500 bg-blue-50'
  }
  const navigate = useNavigate()
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
        <AlertTriangle size={18} className="text-indigo-500" />
        {t('alerts.title')}
        {alerts.length > 0 && (
          <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">{alerts.length}</span>
        )}
      </h2>
      {alerts.length === 0 ? (
        <div className="flex flex-col items-center py-8 text-center">
          <CheckCircle2 size={36} className="text-green-400" />
          <p className="mt-2 text-sm text-gray-500">{t('alerts.noAlerts')}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {alerts.map((a, idx) => (
            <div key={idx} className={`flex items-start gap-3 rounded-lg border-l-4 p-3 ${bgFor(a.level)}`}>
              <div className="mt-0.5 shrink-0">{iconFor(a.level)}</div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-800">{a.title}</p>
                <p className="text-xs text-gray-600">{a.message}</p>
              </div>
              {a.action && (
                <button
                  onClick={() => navigate(a.action)}
                  className="flex shrink-0 items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800"
                >
                  {t('alerts.goToDetail')} <ArrowRight size={12} />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
