import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { pageOperationLogs, getOperationLogDetail, getModules } from '../api/operationLog'
import {
  ScrollText, Search, X, AlertTriangle, CheckCircle2, XCircle,
  Ban, RefreshCw, ShieldAlert, Eye, Filter,
} from 'lucide-react'

const STATUS_CONFIG = {
  SUCCESS: { statusKey: 'status.SUCCESS', color: 'bg-green-50 text-green-700', icon: CheckCircle2 },
  FAILED: { statusKey: 'status.FAILED', color: 'bg-red-50 text-red-700', icon: XCircle },
  BLOCKED: { statusKey: 'status.BLOCKED', color: 'bg-orange-50 text-orange-700', icon: Ban },
  PARTIAL_SUCCESS: { statusKey: 'status.PARTIAL_SUCCESS', color: 'bg-amber-50 text-amber-700', icon: AlertTriangle },
}

const OP_TYPES = ['', 'CREATE', 'UPDATE', 'DELETE', 'RENAME', 'QUERY', 'IMPORT', 'EXPORT', 'CLEAR', 'EXECUTE', 'BLOCKED', 'LOGIN', 'LOGOUT', 'TEST_CONNECTION']
const STATUSES = ['', 'SUCCESS', 'FAILED', 'BLOCKED', 'PARTIAL_SUCCESS']

export default function OperationLog() {
  const { t, i18n } = useTranslation('operationLog')
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [loading, setLoading] = useState(false)
  const [modules, setModules] = useState([])
  const [detailModal, setDetailModal] = useState(null)
  const [toast, setToast] = useState(null)

  const [filters, setFilters] = useState({})
  const [showFilters, setShowFilters] = useState(false)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const loadModules = useCallback(async () => {
    try { const res = await getModules(); setModules(res.data || []) }
    catch (e) { setModules([]) }
  }, [])
  useEffect(() => { loadModules() }, [loadModules])

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const params = { page, size, ...filters }
      const res = await pageOperationLogs(params)
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) { showToast('error', e.message); setRows([]) }
    finally { setLoading(false) }
  }, [page, size, filters])
  useEffect(() => { loadData() }, [loadData])

  const onFilterChange = (key, val) => {
    setFilters((prev) => {
      const next = { ...prev }
      if (val) next[key] = val; else delete next[key]
      return next
    })
    setPage(1)
  }

  const onReset = () => { setFilters({}); setPage(1) }

  const onViewDetail = async (id) => {
    try {
      const res = await getOperationLogDetail(id)
      setDetailModal(res.data)
    } catch (e) { showToast('error', e.message) }
  }

  const totalPages = Math.ceil(total / size) || 1
  const locale = i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN'

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <ScrollText size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowFilters((v) => !v)}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <Filter size={15} /> {t('filterBtn')} {Object.keys(filters).length > 0 && <span className="ml-1 rounded-full bg-indigo-500 px-1.5 text-[10px] text-white">{Object.keys(filters).length}</span>}
          </button>
          <button onClick={loadData} disabled={loading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
        </div>
      </header>

      {showFilters && (
        <div className="flex flex-wrap items-center gap-2 border-b border-gray-100 bg-gray-50 px-6 py-3">
          <select value={filters.module || ''} onChange={(e) => onFilterChange('module', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            <option value="">{t('filterAllModule')}</option>
            {modules.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <select value={filters.operationType || ''} onChange={(e) => onFilterChange('operationType', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            {OP_TYPES.map((tt) => <option key={tt} value={tt}>{tt || t('filterAllType')}</option>)}
          </select>
          <select value={filters.status || ''} onChange={(e) => onFilterChange('status', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            {STATUSES.map((s) => <option key={s} value={s}>{s || t('filterAllStatus')}</option>)}
          </select>
          <input value={filters.username || ''} onChange={(e) => onFilterChange('username', e.target.value)}
            placeholder={t('filterOperatorPlaceholder')} className="w-24 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <input value={filters.connectionId || ''} onChange={(e) => onFilterChange('connectionId', e.target.value)}
            placeholder={t('filterConnIdPlaceholder')} className="w-20 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <input value={filters.keyword || ''} onChange={(e) => onFilterChange('keyword', e.target.value)}
            placeholder={t('filterKeywordPlaceholder')} className="w-32 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <select value={filters.isDangerous == null ? '' : String(filters.isDangerous)} onChange={(e) => onFilterChange('isDangerous', e.target.value === 'true' ? true : e.target.value === 'false' ? false : '')}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            <option value="">{t('filterAll')}</option>
            <option value="true">{t('filterDangerousOnly')}</option>
            <option value="false">{t('filterNormalOnly')}</option>
          </select>
          {Object.keys(filters).length > 0 && (
            <button onClick={onReset} className="rounded bg-gray-200 px-2 py-1 text-xs text-gray-500 hover:bg-gray-300">{t('common:clear')}</button>
          )}
        </div>
      )}

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <div className="max-h-full overflow-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-3 py-3">{t('col.time')}</th>
                  <th className="px-3 py-3">{t('col.operator')}</th>
                  <th className="px-3 py-3">{t('col.connection')}</th>
                  <th className="px-3 py-3">{t('col.module')}</th>
                  <th className="px-3 py-3">{t('col.type')}</th>
                  <th className="px-3 py-3">{t('col.object')}</th>
                  <th className="px-3 py-3">{t('col.affected')}</th>
                  <th className="px-3 py-3">{t('col.status')}</th>
                  <th className="px-3 py-3">{t('col.cost')}</th>
                  <th className="px-3 py-3 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {loading && <tr><td colSpan={10} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>}
                {!loading && rows.length === 0 && <tr><td colSpan={10} className="px-4 py-12 text-center text-gray-400">{t('empty')}</td></tr>}
                {!loading && rows.map((row) => {
                  const sc = STATUS_CONFIG[row.status] || STATUS_CONFIG.FAILED
                  return (
                    <tr key={row.id} className="hover:bg-gray-50">
                      <td className="px-3 py-3 text-xs text-gray-500 whitespace-nowrap">{formatTime(row.createTime, locale)}</td>
                      <td className="px-3 py-3 text-xs text-gray-700">{row.username || '-'}</td>
                      <td className="px-3 py-3 text-xs text-gray-600 truncate max-w-[120px]" title={row.connectionName}>{row.connectionName || '-'}</td>
                      <td className="px-3 py-3 text-xs text-gray-700">{row.module || '-'}</td>
                      <td className="px-3 py-3">
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-600">{row.operationType || '-'}</span>
                      </td>
                      <td className="px-3 py-3 text-xs text-gray-700">
                        <div className="flex items-center gap-1">
                          {row.isDangerous && <ShieldAlert size={12} className="text-red-500" />}
                          <span className="truncate max-w-[200px]" title={row.objectName}>{row.objectName || row.action || '-'}</span>
                        </div>
                      </td>
                      <td className="px-3 py-3 text-xs text-gray-500">{row.affectedCount != null ? row.affectedCount : '-'}</td>
                      <td className="px-3 py-3">
                        <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-medium ${sc.color}`}>
                          <sc.icon size={11} /> {t(sc.statusKey)}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-xs text-gray-500">{row.costMs != null ? `${row.costMs}ms` : '-'}</td>
                      <td className="px-3 py-3">
                        <button onClick={() => onViewDetail(row.id)} title={t('common:detail')}
                          className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-indigo-50 hover:text-indigo-600">
                          <Eye size={14} />
                        </button>
                      </td>
                    </tr>
                  )
                })}
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

      {detailModal && (
        <DetailModal data={detailModal} onClose={() => setDetailModal(null)} />
      )}

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function DetailModal({ data, onClose }) {
  const { t } = useTranslation('operationLog')
  const sc = STATUS_CONFIG[data.status] || STATUS_CONFIG.FAILED
  let detailJson = null
  try { detailJson = data.detail ? JSON.parse(data.detail) : null } catch (_) { detailJson = data.detail }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="flex items-center gap-2 text-base font-semibold text-gray-800">
            <ScrollText size={18} className="text-indigo-500" />
            {t('detailTitle', { id: data.id })}
            {data.isDangerous && (
              <span className="flex items-center gap-1 rounded bg-red-50 px-2 py-0.5 text-xs font-medium text-red-600">
                <ShieldAlert size={12} /> {t('dangerousOp')}
              </span>
            )}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="max-h-[75vh] overflow-y-auto px-6 py-4 space-y-5">
          <Section title={t('section.basic')}>
            <Field label={t('field.opTime')} value={formatTime(data.createTime)} />
            <Field label={t('field.cost')} value={data.costMs != null ? `${data.costMs}ms` : '-'} />
            <Field label={t('field.status')}>
              <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium ${sc.color}`}>
                <sc.icon size={12} /> {t(sc.statusKey)}
              </span>
            </Field>
            <Field label={t('field.operator')} value={`${data.username || '-'} (ID: ${data.userId || '-'})`} />
            <Field label={t('field.clientIp')} value={data.ip || '-'} />
            {data.userAgent && <Field label={t('field.userAgent')} value={data.userAgent} />}
          </Section>

          <Section title={t('section.opContent')}>
            <Field label={t('field.module')} value={data.module || '-'} />
            <Field label={t('field.opType')} value={data.operationType || '-'} />
            <Field label={t('field.opName')} value={data.operationName || data.action || '-'} />
            <Field label={t('field.httpMethod')} value={data.method || '-'} />
            {data.isDangerous && (
              <Field label={t('field.isDangerous')}>
                <span className="flex items-center gap-1 text-xs font-medium text-red-600">
                  <ShieldAlert size={12} /> {t('common:yes')}
                </span>
              </Field>
            )}
          </Section>

          {(data.connectionName || data.connectionId || data.jdbcUrlMasked) && (
            <Section title={t('section.conn')}>
              <Field label={t('field.connName')} value={data.connectionName || '-'} />
              <Field label={t('field.connId')} value={data.connectionId || '-'} />
              {data.jdbcUrlMasked && <Field label="JDBC URL" value={data.jdbcUrlMasked} mono />}
            </Section>
          )}

          {(data.schemaName || data.objectType || data.objectName || data.objectId) && (
            <Section title={t('section.opObject')}>
              {data.schemaName && <Field label={t('field.schema')} value={data.schemaName} />}
              {data.objectType && <Field label={t('field.objectType')} value={data.objectType} />}
              {data.objectName && <Field label={t('field.objectName')} value={data.objectName} />}
              {data.objectId && <Field label={t('field.objectId')} value={data.objectId} mono />}
            </Section>
          )}

          {data.affectedCount != null && (
            <Section title={t('section.result')}>
              <Field label={t('field.affectedCount')} value={data.affectedCount} />
            </Section>
          )}

          {data.errorMessage && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4">
              <div className="mb-1 flex items-center gap-1 text-xs font-medium text-red-600">
                <XCircle size={14} /> {t('field.errorMsg')}
              </div>
              <pre className="whitespace-pre-wrap text-sm text-red-600">{data.errorMessage}</pre>
            </div>
          )}

          {detailJson && (
            <Section title={t('section.opDetail')}>
              <pre className="overflow-auto rounded-lg bg-gray-900 p-3 text-xs text-green-400">
                <code>{typeof detailJson === 'string' ? detailJson : JSON.stringify(detailJson, null, 2)}</code>
              </pre>
            </Section>
          )}

          {data.params && (
            <Section title={t('section.params')}>
              <pre className="overflow-auto rounded-lg bg-gray-50 p-3 text-xs text-gray-600">
                <code>{data.params}</code>
              </pre>
            </Section>
          )}
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-gray-700">{title}</h3>
      <div className="grid grid-cols-2 gap-x-4 gap-y-2">{children}</div>
    </div>
  )
}

function Field({ label, value, mono, children }) {
  return (
    <div>
      <span className="text-xs text-gray-400">{label}: </span>
      {children != null ? children : (
        <span className={`text-sm text-gray-700 ${mono ? 'font-mono text-xs' : ''}`}>{value}</span>
      )}
    </div>
  )
}

function formatTime(time, locale) {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleString(locale || 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
