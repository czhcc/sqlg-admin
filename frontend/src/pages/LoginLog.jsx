import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { pageLoginLogs } from '../api/loginLog'
import {
  LogIn, Search, RefreshCw, CheckCircle2, XCircle,
} from 'lucide-react'

export default function LoginLog() {
  const { t, i18n } = useTranslation('loginLog')
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = { page, size }
      if (keyword) params.keyword = keyword
      if (statusFilter) params.resultStatus = statusFilter
      const res = await pageLoginLogs(params)
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) { showToast('error', e.message) }
    finally { setLoading(false) }
  }, [page, size, keyword, statusFilter])

  useEffect(() => { load() }, [load])

  const totalPages = Math.ceil(total / size) || 1
  const locale = i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN'

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <LogIn size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && (setPage(1), load())}
              placeholder={t('searchPlaceholder')}
              className="w-48 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500" />
          </div>
          <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1) }}
            className="rounded-md border border-gray-300 px-2 py-1.5 text-sm outline-none focus:border-indigo-500">
            <option value="">{t('resultAll')}</option>
            <option value="SUCCESS">{t('resultSuccess')}</option>
            <option value="FAILED">{t('resultFailed')}</option>
          </select>
          <button onClick={load}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
              <tr>
                <th className="px-4 py-3">{t('col.loginTime')}</th>
                <th className="px-4 py-3">{t('col.username')}</th>
                <th className="px-4 py-3">{t('col.ip')}</th>
                <th className="px-4 py-3">{t('col.result')}</th>
                <th className="px-4 py-3">{t('col.failReason')}</th>
                <th className="px-4 py-3">{t('col.userAgent')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && <tr><td colSpan={6} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>}
              {!loading && rows.length === 0 && <tr><td colSpan={6} className="px-4 py-12 text-center text-gray-400">{t('empty')}</td></tr>}
              {!loading && rows.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-xs text-gray-600 whitespace-nowrap">{formatTime(r.loginTime, locale)}</td>
                  <td className="px-4 py-3 text-sm text-gray-700">{r.username || '—'}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{r.clientIp || '—'}</td>
                  <td className="px-4 py-3">
                    {r.resultStatus === 'SUCCESS'
                      ? <span className="inline-flex items-center gap-1 rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700"><CheckCircle2 size={11} /> {t('resultSuccess')}</span>
                      : <span className="inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-xs text-red-700"><XCircle size={11} /> {t('resultFailed')}</span>}
                  </td>
                  <td className="px-4 py-3 text-xs text-red-500">{r.failReason || '—'}</td>
                  <td className="max-w-xs truncate px-4 py-3 text-xs text-gray-500" title={r.userAgent}>{r.userAgent || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
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

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>{toast.message}</div>
      )}
    </div>
  )
}

function formatTime(time, locale) {
  if (!time) return '—'
  const d = new Date(time)
  return d.toLocaleString(locale, { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
