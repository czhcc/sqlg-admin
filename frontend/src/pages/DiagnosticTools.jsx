import { useEffect, useState, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  listConnections, listChecks, runDiagnostic, getReport, listRecentReports, getTaskStatus,
  downloadReportJson, downloadReportExcel, compareReports, getStats,
} from '../api/diagnostic'
import {
  RefreshCw, Play, ChevronDown, ChevronRight, CheckCircle2, XCircle, Loader,
  AlertTriangle, AlertOctagon, Info, ExternalLink, Stethoscope, ListChecks,
  Download, FileSpreadsheet, GitCompare, BarChart3, TrendingUp, TrendingDown, Minus,
} from 'lucide-react'

const POLL_INTERVAL = 2000
const TERMINAL_STATUSES = ['SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED']

export default function DiagnosticTools() {
  const { t } = useTranslation('diagnosticTools')
  const navigate = useNavigate()
  const [tab, setTab] = useState('newRun')
  const [connections, setConnections] = useState([])
  const [connectionId, setConnectionId] = useState('')
  const [checkGroups, setCheckGroups] = useState([])
  const [selectedChecks, setSelectedChecks] = useState(new Set())
  const [running, setRunning] = useState(false)
  const [currentTask, setCurrentTask] = useState(null)
  const [report, setReport] = useState(null)
  const [reports, setReports] = useState([])
  const [reportsTotal, setReportsTotal] = useState(0)
  const [reportsPage, setReportsPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)
  const pollRef = useRef(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const loadInitial = useCallback(async () => {
    setLoading(true)
    try {
      const [connRes, checkRes] = await Promise.all([listConnections(), listChecks()])
      setConnections(connRes.data?.connections || [])
      const groups = checkRes.data || []
      setCheckGroups(groups)
      const defaultConn = (connRes.data?.connections || []).find((c) => c.isDefault)
      if (defaultConn) setConnectionId(String(defaultConn.id))
    } catch (e) {
      showToast('error', t('msg.loadFail', { message: e.message }))
    } finally {
      setLoading(false)
    }
  }, [t])

  const loadReports = useCallback(async () => {
    try {
      const res = await listRecentReports({ page: reportsPage, size: 10 })
      setReports(res.data?.rows || [])
      setReportsTotal(res.data?.total || 0)
    } catch (e) {
      showToast('error', t('msg.loadFail', { message: e.message }))
    }
  }, [reportsPage, t])

  useEffect(() => { loadInitial() }, [loadInitial])
  useEffect(() => { if (tab === 'reports') loadReports() }, [tab, loadReports])

  const allCheckIds = checkGroups.flatMap((g) => g.checks.map((c) => c.id))
  const allSelected = selectedChecks.size === allCheckIds.length && allCheckIds.length > 0

  const toggleCheck = (id) => {
    setSelectedChecks((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }
  const toggleCategory = (category) => {
    const group = checkGroups.find((g) => g.category === category)
    if (!group) return
    const ids = group.checks.map((c) => c.id)
    setSelectedChecks((prev) => {
      const next = new Set(prev)
      const allIn = ids.every((id) => next.has(id))
      if (allIn) ids.forEach((id) => next.delete(id))
      else ids.forEach((id) => next.add(id))
      return next
    })
  }
  const toggleAll = () => {
    if (allSelected) setSelectedChecks(new Set())
    else setSelectedChecks(new Set(allCheckIds))
  }

  const canRun = !!connectionId && selectedChecks.size > 0 && !running

  const onRun = async () => {
    if (!connectionId) { showToast('error', t('noConnection')); return }
    if (selectedChecks.size === 0) { showToast('error', t('noChecksSelected')); return }
    setRunning(true)
    setReport(null)
    setCurrentTask(null)
    try {
      const res = await runDiagnostic({
        connectionId: Number(connectionId),
        checkIds: Array.from(selectedChecks),
      })
      setCurrentTask(res.data)
      startPolling(res.data.id)
    } catch (e) {
      showToast('error', t('msg.runFail', { message: e.message }))
      setRunning(false)
    }
  }

  const startPolling = (taskId) => {
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      try {
        const res = await getTaskStatus(taskId)
        setCurrentTask(res.data)
        if (TERMINAL_STATUSES.includes(res.data?.status)) {
          if (pollRef.current) clearInterval(pollRef.current)
          pollRef.current = null
          setRunning(false)
          const rep = await getReport(taskId)
          setReport(rep.data)
          loadReports()
        }
      } catch (e) {
        if (pollRef.current) clearInterval(pollRef.current)
        pollRef.current = null
        setRunning(false)
        showToast('error', t('msg.loadReportFail', { message: e.message }))
      }
    }, POLL_INTERVAL)
  }

  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current) }, [])

  const viewReport = async (taskId) => {
    try {
      const taskRes = await getTaskStatus(taskId)
      const repRes = await getReport(taskId)
      setCurrentTask(taskRes.data)
      setReport(repRes.data)
      setTab('newRun')
    } catch (e) {
      showToast('error', t('msg.loadReportFail', { message: e.message }))
    }
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Stethoscope size={20} className="text-indigo-600" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <button onClick={loadInitial} disabled={loading}
          className="flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
        </button>
      </header>

      <div className="flex items-center gap-1 border-b border-gray-200 bg-white px-6">
        {['newRun', 'reports', 'compare', 'stats'].map((key) => (
          <button key={key} onClick={() => setTab(key)}
            className={`-mb-px border-b-2 px-4 py-2.5 text-sm transition-colors ${
              tab === key
                ? 'border-indigo-600 text-indigo-700'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {t(`tab.${key}`)}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-auto bg-gray-50 p-6">
        {tab === 'newRun' ? (
          <NewRunTab
            t={t}
            connections={connections}
            connectionId={connectionId}
            setConnectionId={setConnectionId}
            checkGroups={checkGroups}
            selectedChecks={selectedChecks}
            toggleCheck={toggleCheck}
            toggleCategory={toggleCategory}
            toggleAll={toggleAll}
            allSelected={allSelected}
            canRun={canRun}
            running={running}
            onRun={onRun}
            currentTask={currentTask}
            report={report}
            navigate={navigate}
          />
        ) : tab === 'reports' ? (
          <ReportsTab
            t={t}
            reports={reports}
            total={reportsTotal}
            page={reportsPage}
            setPage={setReportsPage}
            onView={viewReport}
          />
        ) : tab === 'compare' ? (
          <CompareTab t={t} reports={reports} showToast={showToast} />
        ) : (
          <StatsTab t={t} connections={connections} showToast={showToast} />
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

function NewRunTab({ t, connections, connectionId, setConnectionId, checkGroups, selectedChecks,
                      toggleCheck, toggleCategory, toggleAll, allSelected, canRun, running, onRun,
                      currentTask, report, navigate }) {
  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <label className="mb-2 block text-sm font-medium text-gray-700">{t('check.selectConnection')}</label>
        <select
          value={connectionId}
          onChange={(e) => setConnectionId(e.target.value)}
          disabled={running}
          className="w-full max-w-md rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100 disabled:bg-gray-50"
        >
          <option value="">{t('noConnection')}</option>
          {connections.map((c) => (
            <option key={c.id} value={c.id}>{c.name}{c.isDefault ? ' (默认)' : ''}</option>
          ))}
        </select>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-medium text-gray-700">
            {t('check.selectChecks')}
            <span className="ml-2 text-xs text-gray-400">
              {t('selected', { count: selectedChecks.size })} · {t('checksTotal', { count: checkGroups.reduce((n, g) => n + g.checks.length, 0) })}
            </span>
          </h2>
          <button onClick={toggleAll} disabled={running}
            className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            {allSelected ? t('selectNone') : t('selectAll')}
          </button>
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {checkGroups.map((group) => {
            const ids = group.checks.map((c) => c.id)
            const selectedInCat = ids.filter((id) => selectedChecks.has(id)).length
            const allIn = selectedInCat === ids.length && ids.length > 0
            const catKey = group.category
            return (
              <div key={catKey} className="rounded-lg border border-gray-200 p-3">
                <div className="mb-2 flex items-center justify-between">
                  <label className="flex cursor-pointer items-center gap-2 text-sm font-semibold text-gray-700">
                    <input type="checkbox" checked={allIn}
                      onChange={() => toggleCategory(catKey)} disabled={running}
                      className="accent-indigo-600" />
                    {t(`category.${catKey}`)}
                    <span className="text-xs font-normal text-gray-400">({selectedInCat}/{ids.length})</span>
                  </label>
                </div>
                <div className="space-y-1.5">
                  {group.checks.map((check) => (
                    <label key={check.id}
                      className={`flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 text-sm transition-colors ${
                        selectedChecks.has(check.id) ? 'bg-indigo-50' : 'hover:bg-gray-50'
                      } ${running ? 'opacity-60' : ''}`}>
                      <input type="checkbox" checked={selectedChecks.has(check.id)}
                        onChange={() => toggleCheck(check.id)} disabled={running}
                        className="mt-0.5 accent-indigo-600" />
                      <div className="flex-1">
                        <div className="font-medium text-gray-800">
                          {check.displayName}
                          {check.isHeavy && (
                            <span className="ml-2 inline-flex items-center rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] text-amber-700">
                              <Loader size={9} className="mr-0.5" /> heavy
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-500">{check.description}</p>
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      </section>

      <div className="flex justify-end">
        <button onClick={onRun} disabled={!canRun}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          <Play size={15} /> {running ? t('running') : t('run')}
        </button>
      </div>

      {currentTask && (
        <ProgressPanel t={t} task={currentTask} />
      )}

      {report && (
        <ReportPanel t={t} data={report} navigate={navigate} />
      )}
    </div>
  )
}

function ProgressPanel({ t, task }) {
  const pct = task.progressPercent || 0
  return (
    <section className="rounded-xl border border-blue-200 bg-blue-50 p-4">
      <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-blue-800">
        <Loader size={15} className={task.status === 'RUNNING' ? 'animate-spin' : ''} />
        {t('progress.title')}
      </h3>
      <div className="flex items-center gap-3">
        <div className="h-2 flex-1 overflow-hidden rounded-full bg-blue-100">
          <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${pct}%` }} />
        </div>
        <span className="text-sm font-medium text-blue-800">{pct}%</span>
      </div>
      <div className="mt-2 grid grid-cols-3 gap-3 text-xs text-blue-700">
        <div>{t('progress.completed')}: {task.processedCount || 0} {t('progress.of')} {task.totalCount || '—'} {t('progress.checks')}</div>
        <div>{t('progress.currentStage')}: {task.currentStage || '—'}</div>
        <div className="text-right">
          <StatusBadge status={task.status} />
        </div>
      </div>
    </section>
  )
}

function ReportPanel({ t, data, navigate }) {
  const { task, report } = data
  if (!report || !report.available) {
    return (
      <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <p className="text-sm text-gray-500">{t('report.notReady')}</p>
        {data.errorMessage && (
          <pre className="mt-2 overflow-x-auto rounded-lg bg-red-50 p-3 text-xs text-red-700">{data.errorMessage}</pre>
        )}
      </section>
    )
  }
  const summary = report.summary || {}
  const issues = report.issues || []
  const bySeverity = summary.bySeverity || {}
  const byCategory = summary.byCategory || {}
  const [expanded, setExpanded] = useState({ INFO: true, WARNING: true, ERROR: true, CRITICAL: true })
  const toggle = (sev) => setExpanded((prev) => ({ ...prev, [sev]: !prev[sev] }))

  const issuesBySeverity = { CRITICAL: [], ERROR: [], WARNING: [], INFO: [] }
  issues.forEach((i) => { if (issuesBySeverity[i.level]) issuesBySeverity[i.level].push(i) })

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-gray-100 pb-3">
        <h3 className="flex items-center gap-2 text-base font-semibold text-gray-800">
          <ListChecks size={18} className="text-indigo-600" /> {t('report.title')}
        </h3>
        <div className="text-right text-xs text-gray-500">
          <div>{task.connectionName || '—'}</div>
          <div>{task.startTime ? String(task.startTime).replace('T', ' ').substring(0, 19) : '—'}</div>
        </div>
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        <SeverityCard level="CRITICAL" count={bySeverity.CRITICAL || 0} t={t} />
        <SeverityCard level="ERROR" count={bySeverity.ERROR || 0} t={t} />
        <SeverityCard level="WARNING" count={bySeverity.WARNING || 0} t={t} />
        <SeverityCard level="INFO" count={bySeverity.INFO || 0} t={t} />
      </div>

      {Object.keys(byCategory).length > 0 && (
        <div className="mb-4 rounded-lg bg-gray-50 p-3">
          <p className="mb-1 text-xs font-medium text-gray-500">{t('report.byCategory')}</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(byCategory).map(([cat, n]) => (
              <span key={cat} className="rounded-full border border-gray-200 bg-white px-2.5 py-0.5 text-xs text-gray-700">
                {t(`category.${cat}`, { defaultValue: cat })}: <strong className="text-gray-900">{n}</strong>
              </span>
            ))}
          </div>
        </div>
      )}

      {issues.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <CheckCircle2 size={48} className="text-green-500" />
          <p className="mt-3 text-base font-medium text-gray-800">{t('report.noIssues')}</p>
          <p className="text-sm text-gray-500">{t('report.noIssuesDesc')}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {['CRITICAL', 'ERROR', 'WARNING', 'INFO'].map((sev) => {
            const list = issuesBySeverity[sev]
            if (!list || list.length === 0) return null
            return (
              <SeveritySection key={sev} level={sev} issues={list} expanded={!!expanded[sev]}
                onToggle={() => toggle(sev)} t={t} navigate={navigate} />
            )
          })}
        </div>
      )}
    </section>
  )
}

function SeverityCard({ level, count, t }) {
  const cfg = {
    CRITICAL: { Icon: AlertOctagon, cls: 'bg-red-50 text-red-700 border-red-200' },
    ERROR: { Icon: XCircle, cls: 'bg-orange-50 text-orange-700 border-orange-200' },
    WARNING: { Icon: AlertTriangle, cls: 'bg-amber-50 text-amber-700 border-amber-200' },
    INFO: { Icon: Info, cls: 'bg-blue-50 text-blue-700 border-blue-200' },
  }
  const { Icon, cls } = cfg[level]
  return (
    <div className={`rounded-lg border p-3 ${cls}`}>
      <div className="flex items-center gap-1.5">
        <Icon size={14} />
        <span className="text-xs font-medium">{t(`severity.${level}`)}</span>
      </div>
      <p className="mt-1 text-2xl font-bold">{count}</p>
    </div>
  )
}

function SeveritySection({ level, issues, expanded, onToggle, t, navigate }) {
  const cfg = {
    CRITICAL: { Icon: AlertOctagon, cls: 'text-red-700 border-red-200 bg-red-50' },
    ERROR: { Icon: XCircle, cls: 'text-orange-700 border-orange-200 bg-orange-50' },
    WARNING: { Icon: AlertTriangle, cls: 'text-amber-700 border-amber-200 bg-amber-50' },
    INFO: { Icon: Info, cls: 'text-blue-700 border-blue-200 bg-blue-50' },
  }
  const { Icon, cls } = cfg[level]
  return (
    <div className={`overflow-hidden rounded-lg border ${cls}`}>
      <button onClick={onToggle}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-semibold">
        {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <Icon size={14} />
        <span className="flex-1">{t(`severity.${level}`)}</span>
        <span className="rounded-full bg-white px-2 py-0.5 text-xs">{issues.length}</span>
      </button>
      {expanded && (
        <div className="divide-y divide-black/5 bg-white">
          {issues.map((issue, idx) => (
            <IssueRow key={idx} issue={issue} t={t} navigate={navigate} />
          ))}
        </div>
      )}
    </div>
  )
}

function IssueRow({ issue, t, navigate }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="px-3 py-2">
      <button onClick={() => setOpen((v) => !v)}
        className="flex w-full items-start gap-2 text-left">
        {open ? <ChevronDown size={14} className="mt-0.5 flex-shrink-0 text-gray-400" /> : <ChevronRight size={14} className="mt-0.5 flex-shrink-0 text-gray-400" />}
        <div className="flex-1">
          <p className="font-mono text-xs text-gray-700">{issue.target}</p>
          <p className="mt-0.5 text-sm text-gray-800">{issue.message}</p>
        </div>
        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-600">{issue.code}</span>
      </button>
      {open && (
        <div className="ml-6 mt-2 space-y-2 border-l-2 border-gray-100 pl-3">
          <div>
            <p className="text-xs font-medium text-gray-500">{t('report.suggestion')}</p>
            <p className="text-sm text-gray-700">{issue.suggestion || t('report.noSuggestion')}</p>
          </div>
          {issue.jumpTarget && (
            <button onClick={() => navigate(issue.jumpTarget)}
              className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800">
              <ExternalLink size={12} /> {t('report.jumpTo')}: {issue.jumpTarget}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function StatusBadge({ status }) {
  const cfg = {
    PENDING: { Icon: Loader, cls: 'bg-gray-50 text-gray-600' },
    RUNNING: { Icon: Loader, cls: 'bg-blue-50 text-blue-600' },
    SUCCESS: { Icon: CheckCircle2, cls: 'bg-green-50 text-green-600' },
    PARTIAL: { Icon: AlertTriangle, cls: 'bg-amber-50 text-amber-600' },
    FAILED: { Icon: XCircle, cls: 'bg-red-50 text-red-600' },
    CANCELLED: { Icon: XCircle, cls: 'bg-gray-50 text-gray-500' },
  }
  const { Icon, cls } = cfg[status] || cfg.PENDING
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${cls}`}>
      <Icon size={11} className={status === 'RUNNING' || status === 'PENDING' ? 'animate-spin' : ''} />
      {status}
    </span>
  )
}

function ReportsTab({ t, reports, total, page, setPage, onView }) {
  const totalPages = Math.max(1, Math.ceil(total / 10))
  const onExport = async (e, taskId, format) => {
    e.stopPropagation()
    try {
      const res = format === 'excel'
        ? await downloadReportExcel(taskId)
        : await downloadReportJson(taskId)
      const blob = new Blob([res], { type: format === 'excel' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = format === 'excel' ? `diagnostic_report_${taskId}.xlsx` : `diagnostic_report_${taskId}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      alert(t('msg.loadReportFail', { message: err.message }))
    }
  }
  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="border-b border-gray-100 px-4 py-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-gray-700">
          <ListChecks size={15} className="text-indigo-600" /> {t('reports.title')}
        </h2>
      </div>
      {reports.length === 0 ? (
        <p className="py-12 text-center text-sm text-gray-400">{t('report.empty')}</p>
      ) : (
        <>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50 text-left text-xs text-gray-500">
                <th className="px-3 py-2 font-medium">{t('reports.taskNo')}</th>
                <th className="px-3 py-2 font-medium">{t('reports.connection')}</th>
                <th className="px-3 py-2 font-medium">{t('reports.status')}</th>
                <th className="px-3 py-2 font-medium">{t('reports.issues')}</th>
                <th className="px-3 py-2 font-medium">{t('reports.startTime')}</th>
                <th className="px-3 py-2 font-medium">{t('reports.creator')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('reports.view')}</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((r) => (
                <tr key={r.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50">
                  <td className="px-3 py-2 font-mono text-xs text-gray-700">{r.taskNo}</td>
                  <td className="px-3 py-2 text-gray-700">{r.connectionName || '—'}</td>
                  <td className="px-3 py-2"><StatusBadge status={r.status} /></td>
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-2 text-xs">
                      <span className="text-red-600">{r.criticalCount || 0}</span>
                      <span className="text-orange-600">{r.errorCount || 0}</span>
                      <span className="text-amber-600">{r.warningCount || 0}</span>
                      <span className="text-gray-400">/ {r.issueCount || 0}</span>
                    </div>
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-500">
                    {r.startTime ? String(r.startTime).replace('T', ' ').substring(0, 19) : '—'}
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-500">{r.username || '—'}</td>
                  <td className="px-3 py-2">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => onView(r.id)} title={t('reports.view')}
                        className="rounded p-1 text-gray-400 hover:bg-indigo-50 hover:text-indigo-600">
                        <ListChecks size={14} />
                      </button>
                      {r.status === 'SUCCESS' && r.issueCount > 0 && (
                        <>
                          <button onClick={(e) => onExport(e, r.id, 'json')} title={t('reports.exportJson')}
                            className="rounded p-1 text-gray-400 hover:bg-blue-50 hover:text-blue-600">
                            <Download size={14} />
                          </button>
                          <button onClick={(e) => onExport(e, r.id, 'excel')} title={t('reports.exportExcel')}
                            className="rounded p-1 text-gray-400 hover:bg-green-50 hover:text-green-600">
                            <FileSpreadsheet size={14} />
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex items-center justify-between border-t border-gray-100 px-4 py-2 text-sm text-gray-500">
            <span>{t('common:total', { count: total })}</span>
            <div className="flex items-center gap-2">
              <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
                className="rounded border border-gray-300 px-2 py-1 hover:bg-gray-50 disabled:opacity-50">
                <ChevronDown size={14} className="-rotate-90" />
              </button>
              <span className="text-xs">{page} / {totalPages}</span>
              <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
                className="rounded border border-gray-300 px-2 py-1 hover:bg-gray-50 disabled:opacity-50">
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function CompareTab({ t, reports, showToast }) {
  const [baselineId, setBaselineId] = useState('')
  const [currentId, setCurrentId] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  const validReports = reports.filter((r) => r.status === 'SUCCESS')
  const canCompare = !!baselineId && !!currentId && baselineId !== currentId

  const onCompare = async () => {
    if (!canCompare) return
    setLoading(true)
    setResult(null)
    try {
      const res = await compareReports(Number(baselineId), Number(currentId))
      setResult(res.data)
    } catch (e) {
      showToast('error', t('msg.loadFail', { message: e.message }))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
          <GitCompare size={15} className="text-indigo-600" /> {t('compare.title')}
        </h2>
        <p className="mb-3 text-sm text-gray-500">{t('compare.desc')}</p>
        <div className="flex flex-wrap items-center gap-3">
          <div>
            <label className="mb-1 block text-xs text-gray-500">{t('compare.baseline')}</label>
            <select value={baselineId} onChange={(e) => setBaselineId(e.target.value)}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-indigo-500">
              <option value="">{t('compare.selectTask')}</option>
              {validReports.map((r) => (
                <option key={r.id} value={r.id}>{r.taskNo} · {r.connectionName || '—'} · {r.startTime ? String(r.startTime).replace('T', ' ').substring(0, 16) : '—'}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-gray-500">{t('compare.current')}</label>
            <select value={currentId} onChange={(e) => setCurrentId(e.target.value)}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-indigo-500">
              <option value="">{t('compare.selectTask')}</option>
              {validReports.map((r) => (
                <option key={r.id} value={r.id}>{r.taskNo} · {r.connectionName || '—'} · {r.startTime ? String(r.startTime).replace('T', ' ').substring(0, 16) : '—'}</option>
              ))}
            </select>
          </div>
          <button onClick={onCompare} disabled={!canCompare || loading}
            className="self-end rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            {loading ? t('compare.comparing') : t('compare.run')}
          </button>
        </div>
        {validReports.length < 2 && (
          <p className="mt-3 text-xs text-amber-600">{t('compare.needTwo')}</p>
        )}
      </section>

      {result && (
        <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <div className="mb-4 grid grid-cols-3 gap-3">
            <CompareStatCard icon={TrendingUp} cls="text-red-600 bg-red-50" label={t('compare.newIssues')} count={result.newCount} />
            <CompareStatCard icon={TrendingDown} cls="text-green-600 bg-green-50" label={t('compare.fixedIssues')} count={result.fixedCount} />
            <CompareStatCard icon={Minus} cls="text-amber-600 bg-amber-50" label={t('compare.persistent')} count={result.persistentCount} />
          </div>
          <CompareIssueList title={t('compare.newIssues')} icon={TrendingUp} cls="border-red-200 bg-red-50 text-red-700"
            issues={result.newIssues} t={t} emptyMsg={t('compare.noNew')} />
          <CompareIssueList title={t('compare.fixedIssues')} icon={TrendingDown} cls="border-green-200 bg-green-50 text-green-700"
            issues={result.fixedIssues} t={t} emptyMsg={t('compare.noFixed')} />
          <CompareIssueList title={t('compare.persistent')} icon={Minus} cls="border-amber-200 bg-amber-50 text-amber-700"
            issues={result.persistentIssues} t={t} emptyMsg={t('compare.noPersistent')} />
        </section>
      )}
    </div>
  )
}

function CompareStatCard({ icon: Icon, cls, label, count }) {
  return (
    <div className={`rounded-lg border p-3 ${cls}`}>
      <div className="flex items-center gap-1.5">
        <Icon size={14} />
        <span className="text-xs font-medium">{label}</span>
      </div>
      <p className="mt-1 text-2xl font-bold">{count}</p>
    </div>
  )
}

function CompareIssueList({ title, icon: Icon, cls, issues, t, emptyMsg }) {
  if (!issues || issues.length === 0) {
    return (
      <div className={`mb-3 rounded-lg border px-3 py-2 text-sm ${cls}`}>
        <div className="flex items-center gap-1.5">
          <Icon size={13} /> <span className="font-medium">{title}</span>
        </div>
        <p className="mt-1 text-xs opacity-70">{emptyMsg}</p>
      </div>
    )
  }
  return (
    <div className={`mb-3 overflow-hidden rounded-lg border ${cls}`}>
      <div className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium">
        <Icon size={13} /> {title} <span className="ml-auto rounded-full bg-white px-2 py-0.5 text-xs">{issues.length}</span>
      </div>
      <div className="divide-y divide-black/5 bg-white">
        {issues.map((issue, idx) => (
          <div key={idx} className="px-3 py-1.5">
            <p className="font-mono text-xs text-gray-700">{issue.target}</p>
            <p className="text-sm text-gray-800">{issue.message}</p>
            {issue.baselineLevel && (
              <p className="text-xs text-gray-400">{t('compare.wasLevel')}: {issue.baselineLevel} → {issue.level}</p>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function StatsTab({ t, connections, showToast }) {
  const [connectionId, setConnectionId] = useState('')
  const [recentLimit, setRecentLimit] = useState(50)
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const res = await getStats(connectionId || null, recentLimit)
      setStats(res.data)
    } catch (e) {
      showToast('error', t('msg.loadFail', { message: e.message }))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700">
          <BarChart3 size={15} className="text-indigo-600" /> {t('stats.title')}
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          <select value={connectionId} onChange={(e) => setConnectionId(e.target.value)}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-indigo-500">
            <option value="">{t('stats.allConnections')}</option>
            {connections.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select value={recentLimit} onChange={(e) => setRecentLimit(Number(e.target.value))}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm outline-none focus:border-indigo-500">
            <option value={20}>{t('stats.lastN', { n: 20 })}</option>
            <option value={50}>{t('stats.lastN', { n: 50 })}</option>
            <option value={100}>{t('stats.lastN', { n: 100 })}</option>
            <option value={200}>{t('stats.lastN', { n: 200 })}</option>
          </select>
          <button onClick={load} disabled={loading}
            className="rounded-md bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
            <RefreshCw size={14} className={`mr-1 inline ${loading ? 'animate-spin' : ''}`} />
            {t('common:refresh')}
          </button>
        </div>
      </section>

      {stats && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            <div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
              <p className="text-xs text-gray-500">{t('stats.tasksAnalyzed')}</p>
              <p className="mt-1 text-2xl font-bold text-gray-900">{stats.analyzedTaskCount}</p>
            </div>
            <div className="rounded-lg border border-red-200 bg-red-50 p-3">
              <p className="text-xs text-red-600">{t('severity.CRITICAL')}</p>
              <p className="mt-1 text-2xl font-bold text-red-700">{stats.bySeverity?.CRITICAL || 0}</p>
            </div>
            <div className="rounded-lg border border-orange-200 bg-orange-50 p-3">
              <p className="text-xs text-orange-600">{t('severity.ERROR')}</p>
              <p className="mt-1 text-2xl font-bold text-orange-700">{stats.bySeverity?.ERROR || 0}</p>
            </div>
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
              <p className="text-xs text-amber-600">{t('severity.WARNING')}</p>
              <p className="mt-1 text-2xl font-bold text-amber-700">{stats.bySeverity?.WARNING || 0}</p>
            </div>
            <div className="rounded-lg border border-blue-200 bg-blue-50 p-3">
              <p className="text-xs text-blue-600">{t('severity.INFO')}</p>
              <p className="mt-1 text-2xl font-bold text-blue-700">{stats.bySeverity?.INFO || 0}</p>
            </div>
          </div>

          {stats.byCategory && Object.keys(stats.byCategory).length > 0 && (
            <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <h3 className="mb-3 text-sm font-semibold text-gray-700">{t('stats.byCategory')}</h3>
              <div className="space-y-2">
                {Object.entries(stats.byCategory).sort((a, b) => b[1] - a[1]).map(([cat, n]) => (
                  <div key={cat} className="flex items-center gap-3">
                    <span className="w-40 text-xs text-gray-600">{t(`category.${cat}`, { defaultValue: cat })}</span>
                    <div className="h-4 flex-1 overflow-hidden rounded-full bg-gray-100">
                      <div className="h-full rounded-full bg-indigo-500" style={{ width: `${Math.min(100, (n / stats.totalIssues) * 100)}%` }} />
                    </div>
                    <span className="w-10 text-right text-xs font-medium text-gray-700">{n}</span>
                  </div>
                ))}
              </div>
            </section>
          )}

          {stats.byCheckId && Object.keys(stats.byCheckId).length > 0 && (
            <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <h3 className="mb-3 text-sm font-semibold text-gray-700">{t('stats.byCheckId')}</h3>
              <div className="space-y-1.5">
                {Object.entries(stats.byCheckId).sort((a, b) => b[1] - a[1]).slice(0, 15).map(([cid, n]) => (
                  <div key={cid} className="flex items-center gap-2 text-xs">
                    <span className="w-64 truncate font-mono text-gray-600" title={cid}>{cid}</span>
                    <span className="rounded-full bg-amber-50 px-2 py-0.5 text-amber-700">{n}</span>
                  </div>
                ))}
              </div>
            </section>
          )}

          {stats.timeline && stats.timeline.length > 0 && (
            <section className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <h3 className="mb-3 text-sm font-semibold text-gray-700">{t('stats.timeline')}</h3>
              <div className="space-y-1">
                {[...stats.timeline].reverse().map((p, idx) => (
                  <div key={idx} className="flex items-center gap-2 text-xs">
                    <span className="w-32 text-gray-500">{p.endTime ? String(p.endTime).replace('T', ' ').substring(0, 16) : '—'}</span>
                    <span className="w-24 truncate text-gray-600" title={p.connectionName}>{p.connectionName || '—'}</span>
                    <div className="h-3 flex-1 overflow-hidden rounded-full bg-gray-100">
                      <div className="h-full rounded-full bg-indigo-500" style={{ width: `${Math.min(100, (p.issueCount / Math.max(...stats.timeline.map((t) => t.issueCount))) * 100)}%` }} />
                    </div>
                    <span className="w-8 text-right font-medium text-gray-700">{p.issueCount}</span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  )
}
