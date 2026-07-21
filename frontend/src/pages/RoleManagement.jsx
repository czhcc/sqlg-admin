import { useEffect, useState, useCallback, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  pageRoles, deleteRole,
} from '../api/roleManagement'
import { useTranslation } from 'react-i18next'
import {
  ShieldCheck, Search, RefreshCw, MoreVertical, Settings, Users,
  List, GitFork, TerminalSquare, AlertTriangle, Trash2,
} from 'lucide-react'

export default function RoleManagement() {
  const { t, i18n } = useTranslation('roleManagement')
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)
  const [openMenuId, setOpenMenuId] = useState(null)
  const [menuPos, setMenuPos] = useState({ top: 0, left: 0 })
  const menuRef = useRef(null)

  const showToast = (type, message) => { setToast({ type, message }); setTimeout(() => setToast(null), 3000) }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = {}
      if (keyword) params.keyword = keyword
      const res = await pageRoles(params)
      setRows(res.data?.rows || [])
    } catch (e) { showToast('error', e.message) }
    finally { setLoading(false) }
  }, [keyword])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setOpenMenuId(null)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const onDelete = async (row) => {
    setOpenMenuId(null)
    if (!window.confirm(t('msg.confirmDelete', { name: row.roleName }))) return
    try {
      await deleteRole(row.id)
      showToast('success', t('msg.deleted'))
      load()
    } catch (e) { showToast('error', e.message) }
  }

  const menuActions = (row) => [
    { label: t('menu.basicInfo'), icon: Settings, action: () => navigate(`/role-detail/${row.id}?tab=basic`) },
    { label: t('menu.members'), icon: Users, action: () => navigate(`/role-detail/${row.id}?tab=members`) },
    { label: t('menu.menus'), icon: List, action: () => navigate(`/role-detail/${row.id}?tab=menus`) },
    { label: t('menu.operations'), icon: ShieldCheck, action: () => navigate(`/role-detail/${row.id}?tab=operations`) },
    { label: t('menu.connections'), icon: GitFork, action: () => navigate(`/role-detail/${row.id}?tab=connections`) },
    { label: t('menu.gremlin'), icon: TerminalSquare, action: () => navigate(`/role-detail/${row.id}?tab=gremlin`) },
    { label: t('menu.dangerous'), icon: AlertTriangle, action: () => navigate(`/role-detail/${row.id}?tab=dangerous`) },
    ...(row.isBuiltin ? [] : [{ label: t('menu.delete'), icon: Trash2, danger: true, action: () => onDelete(row) }]),
  ]

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <ShieldCheck size={20} className="text-indigo-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={16} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
              placeholder={t("searchPlaceholder")}
              className="w-56 rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-indigo-500" />
          </div>
          <button onClick={load}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> {t('common:refresh')}
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <div className="overflow-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                <tr>
                  <th className="px-4 py-3">{t('col.roleKey')}</th>
                  <th className="px-4 py-3">{t('col.roleName')}</th>
                  <th className="px-4 py-3">{t('col.status')}</th>
                  <th className="px-4 py-3">{t('col.userCount')}</th>
                  <th className="px-4 py-3">{t('col.connCount')}</th>
                  <th className="px-4 py-3">{t('col.permCount')}</th>
                  <th className="px-4 py-3">{t('col.updateTime')}</th>
                  <th className="px-4 py-3 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {loading && <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>}
                {!loading && rows.length === 0 && <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">{t('empty')}</td></tr>}
                {!loading && rows.map((row) => (
                  <tr key={row.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <span className="font-mono text-xs text-gray-600">{row.roleKey}</span>
                      {row.isBuiltin && <span className="ml-1 rounded bg-indigo-50 px-1 text-[10px] text-indigo-600">{t('builtin')}</span>}
                    </td>
                    <td className="px-4 py-3 font-medium text-gray-800">{row.roleName}</td>
                    <td className="px-4 py-3">
                      {row.status === 1
                        ? <span className="rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">{t('common:enable')}</span>
                        : <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{t('common:disable')}</span>}
                    </td>
                    <td className="px-4 py-3 text-gray-600">{row.userCount ?? 0}</td>
                    <td className="px-4 py-3 text-gray-600">{row.connectionCount ?? 0}</td>
                    <td className="px-4 py-3 text-gray-600">{row.permissionCount ?? 0}</td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatTime(row.updateTime, i18n.language?.startsWith('en') ? 'en-US' : 'zh-CN')}</td>
                    <td className="px-4 py-3 relative">
                      <button
                        onClick={(e) => {
                          if (openMenuId === row.id) { setOpenMenuId(null); return }
                          const rect = e.currentTarget.getBoundingClientRect()
                          setMenuPos({ top: rect.bottom + 4, left: rect.right - 176 })
                          setOpenMenuId(row.id)
                        }}
                        className="flex h-7 w-7 items-center justify-center rounded text-gray-400 hover:bg-indigo-50 hover:text-indigo-600"
                      >
                        <MoreVertical size={16} />
                      </button>
                      {openMenuId === row.id && createPortal(
                        <div ref={menuRef}
                          className="fixed z-50 w-44 rounded-lg border border-gray-200 bg-white py-1 shadow-xl"
                          style={{ top: menuPos.top, left: menuPos.left }}
                        >
                          {menuActions(row).map((item, i) => (
                            <button key={i}
                              onClick={() => { setOpenMenuId(null); item.action() }}
                              className={`flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors ${
                                item.danger ? 'text-red-600 hover:bg-red-50' : 'text-gray-700 hover:bg-gray-50'
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
  return d.toLocaleString(locale || 'zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
