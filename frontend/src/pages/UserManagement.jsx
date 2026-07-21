import { useEffect, useState, useCallback, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import {
  pageUsers, updateUserStatus, deleteUser,
} from '../api/userManagement'
import {
  Users, Plus, Search, RefreshCw, MoreVertical, Pencil, Power,
  KeyRound, ShieldCheck, GitFork, ScrollText, LogIn, Trash2,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'

export default function UserManagement() {
  const { t, i18n } = useTranslation('userManagement')
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [size] = useState(20)
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)
  const [openMenuId, setOpenMenuId] = useState(null)
  const [menuPos, setMenuPos] = useState({ top: 0, left: 0 })
  const menuRef = useRef(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = { page, size }
      if (keyword) params.keyword = keyword
      if (statusFilter !== '') params.status = Number(statusFilter)
      const res = await pageUsers(params)
      setRows(res.data?.rows || [])
      setTotal(res.data?.total || 0)
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setLoading(false)
    }
  }, [page, size, keyword, statusFilter])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setOpenMenuId(null)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const onToggleStatus = async (row) => {
    setOpenMenuId(null)
    const next = row.status === 1 ? 0 : 1
    try {
      await updateUserStatus(row.id, next)
      showToast('success', next === 1 ? t('msg.enabled') : t('msg.disabled'))
      load()
    } catch (e) { showToast('error', e.message) }
  }

  const onDelete = async (row) => {
    setOpenMenuId(null)
    if (!window.confirm(t('msg.confirmDelete', { name: row.username }))) return
    try {
      await deleteUser(row.id)
      showToast('success', t('msg.deleted'))
      load()
    } catch (e) { showToast('error', e.message) }
  }

  const totalPages = Math.ceil(total / size) || 1
  const locale = i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN'

  const menuItems = (row) => [
    { label: t('menu.editUser'), icon: Pencil, action: () => navigate(`/user-detail/${row.id}`) },
    {
      label: row.status === 1 ? t('menu.disableUser') : t('menu.enableUser'),
      icon: Power,
      action: () => onToggleStatus(row),
    },
    { label: t('menu.resetPassword'), icon: KeyRound, action: () => navigate(`/user-detail/${row.id}?tab=roles`) },
    { label: t('menu.assignRoles'), icon: Users, action: () => navigate(`/user-detail/${row.id}?tab=roles`) },
    { label: t('menu.viewPermissions'), icon: ShieldCheck, action: () => navigate(`/user-detail/${row.id}?tab=permissions`) },
    { label: t('menu.viewConnections'), icon: GitFork, action: () => navigate(`/user-detail/${row.id}?tab=connections`) },
    { label: t('menu.viewLoginLogs'), icon: LogIn, action: () => navigate(`/user-detail/${row.id}?tab=login-logs`) },
    { label: t('menu.viewOperationLogs'), icon: ScrollText, action: () => navigate(`/user-detail/${row.id}?tab=operation-logs`) },
    { label: t('menu.deleteUser'), icon: Trash2, danger: true, action: () => onDelete(row) },
  ]

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Users size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && (setPage(1), load())}
              placeholder={t("searchPlaceholder")}
              className="w-56 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(1) }}
            className="rounded-md border border-gray-300 px-2 py-1.5 text-sm outline-none focus:border-indigo-500"
          >
            <option value="">{t('statusAll')}</option>
            <option value="1">{t('common:enable')}</option>
            <option value="0">{t('common:disable')}</option>
          </select>
          <button onClick={load}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
          <button onClick={() => navigate('/user-detail/new')}
            className="flex items-center gap-1 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700">
            <Plus size={15} /> {t('addUser')}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <div className="overflow-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-4 py-3">{t('col.username')}</th>
                  <th className="px-4 py-3">{t('col.nickname')}</th>
                  <th className="px-4 py-3">{t('col.status')}</th>
                  <th className="px-4 py-3">{t('col.roles')}</th>
                  <th className="px-4 py-3">{t('col.connections')}</th>
                  <th className="px-4 py-3">{t('col.lastLogin')}</th>
                  <th className="px-4 py-3">{t('col.createTime')}</th>
                  <th className="px-4 py-3 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {loading && (
                  <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>
                )}
                {!loading && rows.length === 0 && (
                  <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('empty')}</td></tr>
                )}
                {!loading && rows.map((row) => (
                  <tr key={row.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-800">{row.username}</td>
                    <td className="px-4 py-3 text-gray-600">{row.nickname || '—'}</td>
                    <td className="px-4 py-3">
                      {row.status === 1
                        ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">{t('common:enable')}</span>
                        : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{t('common:disable')}</span>}
                    </td>
                    <td className="px-4 py-3">
                      {row.roles?.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {row.roles.map((r) => (
                            <span key={r.key} className="rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-700">{r.label}</span>
                          ))}
                        </div>
                      ) : <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-4 py-3 text-gray-600">{row.accessibleConnectionCount ?? 0}</td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatTime(row.lastLoginTime, locale)}</td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatTime(row.createTime, locale)}</td>
                    <td className="px-4 py-3 relative" ref={openMenuId === row.id ? menuRef : null}>
                      <button
                        onClick={(e) => {
                          if (openMenuId === row.id) {
                            setOpenMenuId(null)
                          } else {
                            const rect = e.currentTarget.getBoundingClientRect()
                            setMenuPos({ top: rect.bottom + 4, left: rect.right - 176 })
                            setOpenMenuId(row.id)
                          }
                        }}
                        className="flex h-7 w-7 items-center justify-center rounded text-gray-400 hover:bg-indigo-50 hover:text-indigo-600"
                      >
                        <MoreVertical size={16} />
                      </button>
                      {openMenuId === row.id && createPortal(
                        <div
                          className="fixed z-50 w-44 rounded-lg border border-gray-200 bg-white py-1 shadow-xl"
                          style={{ top: menuPos.top, left: menuPos.left }}
                          ref={menuRef}
                        >
                          {menuItems(row).map((item, i) => (
                            <button
                              key={i}
                              onClick={() => { setOpenMenuId(null); item.action() }}
                              className={`flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors ${
                                item.danger
                                  ? 'text-red-600 hover:bg-red-50'
                                  : 'text-gray-700 hover:bg-gray-50'
                              }`}
                            >
                              <item.icon size={15} /> {item.label}
                            </button>
                          ))}
                        </div>,
                        document.body
                      )}
                    </td>
                  </tr>
                ))}
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

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function formatTime(time, locale) {
  if (!time) return '—'
  const d = new Date(time)
  return d.toLocaleString(locale || 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
