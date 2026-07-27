import { useEffect, useState, useCallback, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import {
  listTasks, getTaskDetail, retryTask, cancelTask, deleteTask, downloadTaskResult,
} from '../api/taskCenter'
import { useAuth } from '../context/AuthContext'
import {
  RefreshCw, X, ChevronLeft, ChevronRight,
  CheckCircle2, XCircle, Loader, AlertTriangle, Circle,
  Download, Trash2, RotateCcw, Ban, Eye, FileText, Users,
} from 'lucide-react'

const STATUSES = ['PENDING', 'RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'CANCELLED']
const POLL_INTERVAL = 3000

export default function TaskCenter() {
  const { t } = useTranslation('taskCenter')
  const { permissions } = useAuth()
  const isAdmin = permissions?.menus?.includes('*')
  const [tasks, setTasks] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [showAllUsers, setShowAllUsers] = useState(false)
  const [filters, setFilters] = useState({ status: '', category: '', connectionId: '', schemaName: '', targetLabel: '', keyword: '', startTime: '', endTime: '' })
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)
  const [detailTask, setDetailTask] = useState(null)
  const pollRef = useRef(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = { page, size, ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '')) }
      if (showAllUsers && isAdmin) params.userId = 'all'
      const res = await listTasks(params)
      setTasks(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setLoading(false)
    }
  }, [page, size, filters, showAllUsers, isAdmin])

  useEffect(() => { load() }, [load])

  const hasActive = tasks.some((t) => t.status === 'PENDING' || t.status === 'RUNNING')
  useEffect(() => {
    if (!hasActive) { if (pollRef.current) clearInterval(pollRef.current); pollRef.current = null; return }
    pollRef.current = setInterval(() => load(), POLL_INTERVAL)
    return () => { if (pollRef.current) clearInterval(pollRef.current) }
  }, [hasActive, load])

  const totalPages = Math.max(1, Math.ceil(total / size))

  const onAction = async (action, task) => {
    try {
      if (action === 'cancel') { await cancelTask(task.id); showToast('success', t('msg.cancelSuccess')) }
      else if (action === 'retry') { await retryTask(task.id); showToast('success', t('msg.retrySuccess')) }
      else if (action === 'delete') {
        if (!window.confirm(t('msg.confirmDelete'))) return
        await deleteTask(task.id); showToast('success', t('msg.deleteSuccess'))
      } else if (action === 'download') {
        const res = await downloadTaskResult(task.id)
        const url = URL.createObjectURL(res.data)
        const a = document.createElement('a')
        a.href = url; a.download = task.resultFilePath?.split('/').pop() || 'result'; a.click()
        URL.revokeObjectURL(url)
      } else if (action === 'detail') {
        const res = await getTaskDetail(task.id)
        setDetailTask(res.data)
      }
      if (action !== 'detail') load()
    } catch (e) {
      const key = action === 'cancel' ? 'msg.cancelFail' : action === 'retry' ? 'msg.retryFail' : action === 'delete' ? 'msg.deleteFail' : 'msg.downloadFail'
      showToast('error', t(key, { message: e.message }))
    }
  }

  const resetFilters = () => {
    setFilters({ status: '', category: '', connectionId: '', schemaName: '', targetLabel: '', keyword: '', startTime: '', endTime: '' })
    setPage(1)
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">{t('title')}</h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          {isAdmin && (
            <label className="flex cursor-pointer items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
              <input
                type="checkbox"
                checked={showAllUsers}
                onChange={(e) => { setShowAllUsers(e.target.checked); setPage(1) }}
                className="accent-indigo-600"
              />
              <Users size={14} /> {t('allUsers', { defaultValue: '全部用户' })}
            </label>
          )}
          <button onClick={load} disabled={loading}
            className="flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto bg-gray-50 p-6">
        <FilterBar filters={filters} setFilters={setFilters} onReset={resetFilters} t={t} />

        <div className="mt-4 overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs text-gray-500">
                <th className="px-3 py-2 font-medium">{t('table.taskNo')}</th>
                <th className="px-3 py-2 font-medium">{t('table.name')}</th>
                <th className="px-3 py-2 font-medium">{t('table.category')}</th>
                <th className="px-3 py-2 font-medium">{t('table.connection')}</th>
                <th className="px-3 py-2 font-medium">{t('table.creator')}</th>
                <th className="px-3 py-2 font-medium">{t('table.status')}</th>
                <th className="px-3 py-2 font-medium">{t('table.progress')}</th>
                <th className="px-3 py-2 text-center font-medium">{t('table.success')}/{t('table.failed')}</th>
                <th className="px-3 py-2 font-medium">{t('table.createTime')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('table.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {tasks.length === 0 ? (
                <tr><td colSpan={10} className="py-12 text-center text-gray-400">{t('common:noData')}</td></tr>
              ) : tasks.map((task) => (
                <tr key={task.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50">
                  <td className="px-3 py-2 font-mono text-xs text-gray-700">{task.taskNo}</td>
                  <td className="px-3 py-2 font-medium text-gray-800">{task.name}</td>
                  <td className="px-3 py-2 text-gray-600">{t(`category.${task.category}`, { defaultValue: task.category })}</td>
                  <td className="px-3 py-2 text-gray-600">{task.connectionName || '—'}</td>
                  <td className="px-3 py-2 text-gray-600">{task.username || '—'}</td>
                  <td className="px-3 py-2"><StatusBadge status={task.status} t={t} /></td>
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-1.5">
                      <div className="h-1.5 w-16 overflow-hidden rounded-full bg-gray-200">
                        <div className="h-full rounded-full bg-indigo-500" style={{ width: `${task.progressPercent || 0}%` }} />
                      </div>
                      <span className="text-xs text-gray-500">{task.progressPercent || 0}%</span>
                    </div>
                  </td>
                  <td className="px-3 py-2 text-center text-xs">
                    <span className="text-green-600">{task.successCount || 0}</span>
                    <span className="text-gray-300"> / </span>
                    <span className="text-red-600">{task.failedCount || 0}</span>
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-500">{fmtTime(task.createTime)}</td>
                  <td className="px-3 py-2">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => onAction('detail', task)} title={t('action.detail')}
                        className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-indigo-600"><Eye size={15} /></button>
                      {(task.status === 'PENDING' || task.status === 'RUNNING') && (
                        <button onClick={() => onAction('cancel', task)} title={t('action.cancel')}
                          className="rounded p-1 text-gray-400 hover:bg-amber-50 hover:text-amber-600"><Ban size={15} /></button>
                      )}
                      {(task.status === 'FAILED' || task.status === 'PARTIAL' || task.status === 'CANCELLED') && (
                        <button onClick={() => onAction('retry', task)} title={t('action.retry')}
                          className="rounded p-1 text-gray-400 hover:bg-blue-50 hover:text-blue-600"><RotateCcw size={15} /></button>
                      )}
                      {task.resultFilePath && task.status !== 'RUNNING' && (
                        <button onClick={() => onAction('download', task)} title={t('action.download')}
                          className="rounded p-1 text-gray-400 hover:bg-green-50 hover:text-green-600"><Download size={15} /></button>
                      )}
                      {task.status !== 'PENDING' && task.status !== 'RUNNING' && (
                        <button onClick={() => onAction('delete', task)} title={t('action.delete')}
                          className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600"><Trash2 size={15} /></button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex items-center justify-between text-sm text-gray-500">
          <span>{t('common:total', { count: total })}</span>
          <div className="flex items-center gap-2">
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border border-gray-300 px-2 py-1 hover:bg-gray-50 disabled:opacity-50"><ChevronLeft size={15} /></button>
            <span>{t('common:pageOf', { current: page, total: totalPages })}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border border-gray-300 px-2 py-1 hover:bg-gray-50 disabled:opacity-50"><ChevronRight size={15} /></button>
          </div>
        </div>
      </div>

      {detailTask && <DetailModal task={detailTask} onClose={() => setDetailTask(null)} t={t} />}
      {toast && <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>{toast.message}</div>}
    </div>
  )
}

function FilterBar({ filters, setFilters, onReset, t }) {
  const set = (k, v) => setFilters((prev) => ({ ...prev, [k]: v }))
  const inputCls = 'rounded-md border border-gray-300 px-2.5 py-1.5 text-sm outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-100'
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-gray-200 bg-white p-3 shadow-sm">
      <select className={inputCls} value={filters.status} onChange={(e) => set('status', e.target.value)}>
        <option value="">{t('filter.allStatus')}</option>
        {STATUSES.map((s) => <option key={s} value={s}>{t(`status.${s}`)}</option>)}
      </select>
      <select className={inputCls} value={filters.category} onChange={(e) => set('category', e.target.value)}>
        <option value="">{t('filter.allCategories')}</option>
        {['IMPORT', 'EXPORT', 'MAINTENANCE', 'STATISTICS', 'DIAGNOSTIC'].map((c) => <option key={c} value={c}>{t(`category.${c}`)}</option>)}
      </select>
      <input className={`${inputCls} w-36`} placeholder={t('filter.connection')} value={filters.connectionId} onChange={(e) => set('connectionId', e.target.value)} />
      <input className={`${inputCls} w-32`} placeholder={t('filter.schema')} value={filters.schemaName} onChange={(e) => set('schemaName', e.target.value)} />
      <input className={`${inputCls} w-32`} placeholder={t('filter.label')} value={filters.targetLabel} onChange={(e) => set('targetLabel', e.target.value)} />
      <input className={`${inputCls} w-36`} placeholder={t('filter.keyword')} value={filters.keyword} onChange={(e) => set('keyword', e.target.value)} />
      <input className={`${inputCls} w-40`} type="date" value={filters.startTime} onChange={(e) => set('startTime', e.target.value)} />
      <input className={`${inputCls} w-40`} type="date" value={filters.endTime} onChange={(e) => set('endTime', e.target.value)} />
      <button onClick={onReset} className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-50">{t('filter.reset')}</button>
    </div>
  )
}

function StatusBadge({ status, t }) {
  const config = {
    PENDING: { icon: Circle, cls: 'bg-gray-50 text-gray-600' },
    RUNNING: { icon: Loader, cls: 'bg-blue-50 text-blue-600' },
    SUCCESS: { icon: CheckCircle2, cls: 'bg-green-50 text-green-600' },
    PARTIAL: { icon: AlertTriangle, cls: 'bg-amber-50 text-amber-600' },
    FAILED: { icon: XCircle, cls: 'bg-red-50 text-red-600' },
    CANCELLED: { icon: Ban, cls: 'bg-gray-50 text-gray-500' },
  }
  const c = config[status] || config.PENDING
  const Icon = c.icon
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${c.cls}`}>
      <Icon size={12} className={status === 'RUNNING' ? 'animate-spin' : ''} />
      {t(`status.${status}`, { defaultValue: status })}
    </span>
  )
}

function DetailModal({ task, onClose, t }) {
  const params = (() => { try { return task.params ? JSON.parse(task.params) : {} } catch { return {} } })()
  const result = (() => { try { return task.resultSummary ? JSON.parse(task.resultSummary) : null } catch { return null } })()
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-gray-800">{t('detail.title')}: {task.taskNo}</h2>
            <p className="text-sm text-gray-500">{task.name}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-4">
          <Section title={t('detail.basicInfo')}>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
              <KV label={t('table.taskNo')} value={task.taskNo} mono />
              <KV label={t('table.category')} value={t(`category.${task.category}`, { defaultValue: task.category })} />
              <KV label={t('table.status')} value={<StatusBadge status={task.status} t={t} />} />
              <KV label={t('table.connection')} value={task.connectionName} />
              <KV label={t('table.schema')} value={task.schemaName} />
              <KV label={t('table.target')} value={task.targetLabel} />
              <KV label={t('table.creator')} value={task.username} />
              <KV label={t('table.createTime')} value={fmtTime(task.createTime)} />
              <KV label={t('table.startTime')} value={fmtTime(task.startTime)} />
              <KV label={t('table.endTime')} value={fmtTime(task.endTime)} />
              <KV label={t('detail.heartbeat')} value={fmtTime(task.lastHeartbeatTime)} />
              {task.parentTaskId && <KV label={t('detail.parentTask')} value={`#${task.parentTaskId}`} />}
            </div>
          </Section>

          <Section title={t('detail.progress')}>
            <div className="flex items-center gap-3">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-gray-200">
                <div className="h-full rounded-full bg-indigo-500 transition-all" style={{ width: `${task.progressPercent || 0}%` }} />
              </div>
              <span className="text-sm font-medium text-gray-700">{task.progressPercent || 0}%</span>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-4">
              <KV label={t('detail.total')} value={task.totalCount ?? '—'} />
              <KV label={t('detail.processed')} value={task.processedCount ?? 0} />
              <KV label={t('table.success')} value={<span className="text-green-600">{task.successCount || 0}</span>} />
              <KV label={t('table.failed')} value={<span className="text-red-600">{task.failedCount || 0}</span>} />
            </div>
            {task.currentStage && <p className="mt-2 text-sm text-gray-500">{t('detail.currentStage')}: {task.currentStage}</p>}
          </Section>

          <Section title={t('detail.execParams')}>
            {Object.keys(params).length > 0 ? (
              <pre className="overflow-x-auto rounded-lg bg-gray-900 p-3 text-xs text-green-400">{JSON.stringify(params, null, 2)}</pre>
            ) : <p className="text-sm text-gray-400">{t('detail.noParams')}</p>}
          </Section>

          {result && (
            <Section title={t('detail.result')}>
              <pre className="overflow-x-auto rounded-lg bg-gray-50 p-3 text-xs text-gray-700">{JSON.stringify(result, null, 2)}</pre>
              {task.resultFilePath && (
                <a href={`/api/task/${task.id}/download`} target="_blank" rel="noreferrer"
                  className="mt-2 inline-flex items-center gap-1 text-sm text-indigo-600 hover:text-indigo-800">
                  <FileText size={14} /> {task.resultFilePath.split('/').pop()}
                </a>
              )}
            </Section>
          )}

          {task.errorMessage && (
            <Section title={t('detail.error')}>
              <div className="rounded-lg border-l-4 border-red-500 bg-red-50 p-3">
                <p className="text-sm text-red-700">{task.errorMessage}</p>
              </div>
            </Section>
          )}
        </div>
      </div>
    </div>,
    document.body
  )
}

function Section({ title, children }) {
  return (
    <div className="mb-5">
      <h3 className="mb-2 text-sm font-semibold text-gray-700">{title}</h3>
      {children}
    </div>
  )
}

function KV({ label, value, mono }) {
  return (
    <div>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`text-sm font-medium text-gray-800 ${mono ? 'font-mono text-xs' : ''}`}>{value ?? '—'}</p>
    </div>
  )
}

function fmtTime(s) {
  if (!s) return '—'
  return String(s).replace('T', ' ').substring(0, 19)
}
