import { useEffect, useState, useCallback } from 'react'
import { pageOperationLogs, getOperationLogDetail, getModules } from '../api/operationLog'
import {
  ScrollText, Search, X, AlertTriangle, CheckCircle2, XCircle,
  Ban, ChevronDown, RefreshCw, ShieldAlert, Eye, Filter,
} from 'lucide-react'

const STATUS_CONFIG = {
  SUCCESS: { label: '成功', color: 'bg-green-50 text-green-700', icon: CheckCircle2 },
  FAILED: { label: '失败', color: 'bg-red-50 text-red-700', icon: XCircle },
  BLOCKED: { label: '拦截', color: 'bg-orange-50 text-orange-700', icon: Ban },
  PARTIAL_SUCCESS: { label: '部分成功', color: 'bg-amber-50 text-amber-700', icon: AlertTriangle },
}

const OP_TYPES = ['', 'CREATE', 'UPDATE', 'DELETE', 'RENAME', 'QUERY', 'IMPORT', 'EXPORT', 'CLEAR', 'EXECUTE', 'BLOCKED', 'LOGIN', 'LOGOUT', 'TEST_CONNECTION']
const STATUSES = ['', 'SUCCESS', 'FAILED', 'BLOCKED', 'PARTIAL_SUCCESS']

export default function OperationLog() {
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

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <ScrollText size={20} className="text-indigo-500" /> 操作日志
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">追踪平台关键操作行为 · 问题排查 · 误操作审计</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowFilters((v) => !v)}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <Filter size={15} /> 筛选 {Object.keys(filters).length > 0 && <span className="ml-1 rounded-full bg-indigo-500 px-1.5 text-[10px] text-white">{Object.keys(filters).length}</span>}
          </button>
          <button onClick={loadData} disabled={loading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> 刷新
          </button>
        </div>
      </header>

      {showFilters && (
        <div className="flex flex-wrap items-center gap-2 border-b border-gray-100 bg-gray-50 px-6 py-3">
          <select value={filters.module || ''} onChange={(e) => onFilterChange('module', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            <option value="">全部模块</option>
            {modules.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <select value={filters.operationType || ''} onChange={(e) => onFilterChange('operationType', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            {OP_TYPES.map((t) => <option key={t} value={t}>{t || '全部类型'}</option>)}
          </select>
          <select value={filters.status || ''} onChange={(e) => onFilterChange('status', e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            {STATUSES.map((s) => <option key={s} value={s}>{s || '全部状态'}</option>)}
          </select>
          <input value={filters.username || ''} onChange={(e) => onFilterChange('username', e.target.value)}
            placeholder="操作人" className="w-24 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <input value={filters.connectionId || ''} onChange={(e) => onFilterChange('connectionId', e.target.value)}
            placeholder="连接ID" className="w-20 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <input value={filters.keyword || ''} onChange={(e) => onFilterChange('keyword', e.target.value)}
            placeholder="关键字" className="w-32 rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500" />
          <select value={filters.isDangerous == null ? '' : String(filters.isDangerous)} onChange={(e) => onFilterChange('isDangerous', e.target.value === 'true' ? true : e.target.value === 'false' ? false : '')}
            className="rounded border border-gray-300 px-2 py-1 text-xs outline-none focus:border-indigo-500">
            <option value="">全部</option>
            <option value="true">仅危险操作</option>
            <option value="false">仅普通操作</option>
          </select>
          {Object.keys(filters).length > 0 && (
            <button onClick={onReset} className="rounded bg-gray-200 px-2 py-1 text-xs text-gray-500 hover:bg-gray-300">清除</button>
          )}
        </div>
      )}

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <div className="max-h-full overflow-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-3 py-3">时间</th>
                  <th className="px-3 py-3">操作人</th>
                  <th className="px-3 py-3">连接</th>
                  <th className="px-3 py-3">模块</th>
                  <th className="px-3 py-3">类型</th>
                  <th className="px-3 py-3">操作对象</th>
                  <th className="px-3 py-3">影响</th>
                  <th className="px-3 py-3">状态</th>
                  <th className="px-3 py-3">耗时</th>
                  <th className="px-3 py-3 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {loading && <tr><td colSpan={10} className="px-4 py-12 text-center text-gray-400">加载中...</td></tr>}
                {!loading && rows.length === 0 && <tr><td colSpan={10} className="px-4 py-12 text-center text-gray-400">暂无日志</td></tr>}
                {!loading && rows.map((row) => {
                  const sc = STATUS_CONFIG[row.status] || STATUS_CONFIG.FAILED
                  return (
                    <tr key={row.id} className="hover:bg-gray-50">
                      <td className="px-3 py-3 text-xs text-gray-500 whitespace-nowrap">{formatTime(row.createTime)}</td>
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
                          <sc.icon size={11} /> {sc.label}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-xs text-gray-500">{row.costMs != null ? `${row.costMs}ms` : '-'}</td>
                      <td className="px-3 py-3">
                        <button onClick={() => onViewDetail(row.id)} title="详情"
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
          <span className="text-gray-500">共 {total} 条,第 {page}/{totalPages} 页</span>
          <div className="flex items-center gap-1">
            <button onClick={() => setPage(1)} disabled={page <= 1}
              className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">首页</button>
            <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
              className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">上一页</button>
            <span className="px-2 text-xs text-gray-500">{page} / {totalPages}</span>
            <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
              className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">下一页</button>
            <button onClick={() => setPage(totalPages)} disabled={page >= totalPages}
              className="rounded border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40">末页</button>
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
  const sc = STATUS_CONFIG[data.status] || STATUS_CONFIG.FAILED
  let detailJson = null
  try { detailJson = data.detail ? JSON.parse(data.detail) : null } catch (_) { detailJson = data.detail }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="flex items-center gap-2 text-base font-semibold text-gray-800">
            <ScrollText size={18} className="text-indigo-500" />
            操作日志详情 #{data.id}
            {data.isDangerous && (
              <span className="flex items-center gap-1 rounded bg-red-50 px-2 py-0.5 text-xs font-medium text-red-600">
                <ShieldAlert size={12} /> 危险操作
              </span>
            )}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <div className="max-h-[75vh] overflow-y-auto px-6 py-4 space-y-5">
          <Section title="基本信息">
            <Field label="操作时间" value={formatTime(data.createTime)} />
            <Field label="耗时" value={data.costMs != null ? `${data.costMs}ms` : '-'} />
            <Field label="状态">
              <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium ${sc.color}`}>
                <sc.icon size={12} /> {sc.label}
              </span>
            </Field>
            <Field label="操作人" value={`${data.username || '-'} (ID: ${data.userId || '-'})`} />
            <Field label="客户端IP" value={data.ip || '-'} />
            {data.userAgent && <Field label="User-Agent" value={data.userAgent} />}
          </Section>

          <Section title="操作内容">
            <Field label="模块" value={data.module || '-'} />
            <Field label="操作类型" value={data.operationType || '-'} />
            <Field label="操作名称" value={data.operationName || data.action || '-'} />
            <Field label="HTTP方法" value={data.method || '-'} />
            {data.isDangerous && (
              <Field label="危险操作">
                <span className="flex items-center gap-1 text-xs font-medium text-red-600">
                  <ShieldAlert size={12} /> 是
                </span>
              </Field>
            )}
          </Section>

          {(data.connectionName || data.connectionId || data.jdbcUrlMasked) && (
            <Section title="图数据库连接">
              <Field label="连接名称" value={data.connectionName || '-'} />
              <Field label="连接ID" value={data.connectionId || '-'} />
              {data.jdbcUrlMasked && <Field label="JDBC URL" value={data.jdbcUrlMasked} mono />}
            </Section>
          )}

          {(data.schemaName || data.objectType || data.objectName || data.objectId) && (
            <Section title="操作对象">
              {data.schemaName && <Field label="Schema" value={data.schemaName} />}
              {data.objectType && <Field label="对象类型" value={data.objectType} />}
              {data.objectName && <Field label="对象名称" value={data.objectName} />}
              {data.objectId && <Field label="对象ID" value={data.objectId} mono />}
            </Section>
          )}

          {data.affectedCount != null && (
            <Section title="执行结果">
              <Field label="影响数量" value={data.affectedCount} />
            </Section>
          )}

          {data.errorMessage && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4">
              <div className="mb-1 flex items-center gap-1 text-xs font-medium text-red-600">
                <XCircle size={14} /> 错误信息
              </div>
              <pre className="whitespace-pre-wrap text-sm text-red-600">{data.errorMessage}</pre>
            </div>
          )}

          {detailJson && (
            <Section title="操作详情">
              <pre className="overflow-auto rounded-lg bg-gray-900 p-3 text-xs text-green-400">
                <code>{typeof detailJson === 'string' ? detailJson : JSON.stringify(detailJson, null, 2)}</code>
              </pre>
            </Section>
          )}

          {data.params && (
            <Section title="请求参数">
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

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
