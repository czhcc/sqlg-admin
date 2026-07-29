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
