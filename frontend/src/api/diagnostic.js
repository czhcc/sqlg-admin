import client from './client'

export function listConnections() {
  return client.get('/diagnostic/connections')
}

export function listChecks() {
  return client.get('/diagnostic/checks')
}

export function runDiagnostic(data) {
  return client.post('/diagnostic/run', data)
}

export function getReport(taskId) {
  return client.get(`/diagnostic/report/${taskId}`)
}

export function listRecentReports(params) {
  return client.get('/diagnostic/reports', { params })
}

export function getTaskStatus(taskId) {
  return client.get(`/task/${taskId}`)
}

export function runSingleCheck(connectionId, checkId) {
  return client.post('/diagnostic/run-single', { connectionId, checkId })
}

export function downloadReportJson(taskId) {
  return client.get(`/diagnostic/report/${taskId}/export`, { responseType: 'blob' })
}

export function downloadReportExcel(taskId) {
  return client.get(`/diagnostic/report/${taskId}/export-excel`, { responseType: 'blob' })
}

export function compareReports(baselineTaskId, currentTaskId) {
  return client.get('/diagnostic/compare', { params: { baselineTaskId, currentTaskId } })
}

export function getStats(connectionId, recentLimit = 50) {
  return client.get('/diagnostic/stats', { params: { connectionId, recentLimit } })
}
