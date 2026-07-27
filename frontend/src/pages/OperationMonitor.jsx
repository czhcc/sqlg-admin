import Placeholder from '../components/Placeholder'
import { useTranslation } from 'react-i18next'

export default function OperationMonitor() {
  const { t } = useTranslation('layout')
  return <Placeholder title={t('menu.operationMonitor')} description={t('menu.operationMonitorDesc', { defaultValue: '' })} />
}
