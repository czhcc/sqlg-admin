import client from './client'

export function listConnections(keyword) {
  return client.get('/connection', { params: keyword ? { keyword } : {} })
}

export function getConnection(id) {
  return client.get(`/connection/${id}`)
}

export function createConnection(data) {
  return client.post('/connection', data)
}

export function updateConnection(id, data) {
  return client.put(`/connection/${id}`, data)
}

export function deleteConnection(id) {
  return client.delete(`/connection/${id}`)
}

export function testConnection(data) {
  return client.post('/connection/test', data)
}

export function testConnectionById(id) {
  return client.post(`/connection/${id}/test`)
}

export function updateConnectionStatus(id, status) {
  return client.put(`/connection/${id}/status`, { status })
}

export function setDefaultConnection(id) {
  return client.put(`/connection/${id}/default`)
}
