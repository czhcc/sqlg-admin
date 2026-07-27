import Placeholder from '../components/Placeholder'
import { useTranslation } from 'react-i18next'

export default function DiagnosticTools() {
  const { t } = useTranslation('layout')
  return <Placeholder title={t('menu.diagnosticTools')} description={t('menu.diagnosticToolsDesc', { defaultValue: '' })} />
}
