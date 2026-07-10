import client from './client'

export function listIoConnections() {
  return client.get('/io/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/io/active-connection', { connectionId })
}

export function getSchemas(connectionId) {
  return client.get(`/io/${connectionId}/schemas`)
}

export function exportVertices(connectionId, schema, label, format) {
  return client.get(`/io/${connectionId}/export/vertex/${schema}/${label}`, { params: { format } })
}

export function exportEdges(connectionId, schema, label, format) {
  return client.get(`/io/${connectionId}/export/edge/${schema}/${label}`, { params: { format } })
}

export function exportTopology(connectionId) {
  return client.get(`/io/${connectionId}/export/topology`)
}

export function importTopology(connectionId, payload) {
  return client.post(`/io/${connectionId}/import/topology`, payload, { timeout: 120000 })
}

export function previewImport(connectionId, payload) {
  return client.post(`/io/${connectionId}/import/preview`, payload)
}

export function importVertices(connectionId, payload) {
  return client.post(`/io/${connectionId}/import/vertices`, payload, { timeout: 120000 })
}

export function importEdges(connectionId, payload) {
  return client.post(`/io/${connectionId}/import/edges`, payload, { timeout: 120000 })
}

export function refreshConnection(connectionId) {
  return client.post(`/io/${connectionId}/refresh`)
}
