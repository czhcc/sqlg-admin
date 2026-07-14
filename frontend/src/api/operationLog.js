import client from './client'

export function pageOperationLogs(params) {
  return client.get('/operation-log', { params })
}

export function getOperationLogDetail(id) {
  return client.get(`/operation-log/${id}`)
}

export function getModules() {
  return client.get('/operation-log/modules')
}
