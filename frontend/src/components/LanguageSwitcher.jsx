import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Globe, Check } from 'lucide-react'

/**
 * Reusable language switcher dropdown.
 * Supports two display variants:
 *   variant="light" — for dark backgrounds (login page sidebar)
 *   variant="dark"  — for light backgrounds (user menu)
 *
 * @author czh
 * @date 2026/0717
 */
export default function LanguageSwitcher({ variant = 'dark', className = '' }) {
  const { i18n, t } = useTranslation()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  const current = i18n.language?.startsWith('en') ? 'en' : 'zh'

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const switchTo = (lng) => {
    i18n.changeLanguage(lng)
    setOpen(false)
  }

  const isLight = variant === 'light'

  const langs = [
    { code: 'zh', label: t('common:chinese'), flag: '🇨🇳' },
    { code: 'en', label: t('common:english'), flag: '🇺🇸' },
  ]

  return (
    <div className={`relative ${className}`} ref={ref}>
      <button
        onClick={() => setOpen((v) => !v)}
        className={`flex items-center gap-1.5 rounded-md px-2 py-1 text-sm transition-colors ${
          isLight
            ? 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
            : 'text-slate-300 hover:bg-slate-700 hover:text-white'
        }`}
        title={t('common:switchLanguage')}
      >
        <Globe size={15} />
        <span>{current === 'zh' ? '中文' : 'EN'}</span>
      </button>
      {open && (
        <div
          className={`absolute bottom-full right-0 mb-1 w-36 rounded-lg border py-1 shadow-2xl ${
            isLight ? 'border-gray-200 bg-white' : 'border-slate-600 bg-slate-800'
          }`}
        >
          {langs.map((l) => (
            <button
              key={l.code}
              onClick={() => switchTo(l.code)}
              className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm transition-colors ${
                isLight
                  ? current === l.code
                    ? 'bg-indigo-50 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-50'
                  : current === l.code
                    ? 'bg-slate-700 text-white'
                    : 'text-slate-300 hover:bg-slate-700 hover:text-white'
              }`}
            >
              <span className="flex items-center gap-2">
                <span>{l.flag}</span>
                <span>{l.label}</span>
              </span>
              {current === l.code && <Check size={14} />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
