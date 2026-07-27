import Placeholder from '../components/Placeholder'
import { useTranslation } from 'react-i18next'

export default function TaskCenter() {
  const { t } = useTranslation('layout')
  return <Placeholder title={t('menu.taskCenter')} description={t('menu.taskCenterDesc', { defaultValue: '' })} />
}
