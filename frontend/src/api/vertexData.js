import client from './client'

export function listVertexDataConnections() {
  return client.get('/vertex-data/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/vertex-data/active-connection', { connectionId })
}

export function getTree(connectionId) {
  return client.get(`/vertex-data/${connectionId}/tree`)
}

export function refreshTree(connectionId) {
  return client.post(`/vertex-data/${connectionId}/refresh`)
}

export function getLabelProperties(connectionId, schema, label) {
  return client.get(`/vertex-data/${connectionId}/${schema}/${label}/properties`)
}

export function pageVertices(connectionId, schema, label, params) {
  return client.get(`/vertex-data/${connectionId}/${schema}/${label}`, { params })
}

export function getVertexDetail(connectionId, vertexId) {
  return client.get(`/vertex-data/${connectionId}/vertex/${encodeURIComponent(vertexId)}`)
}

export function createVertex(connectionId, data) {
  return client.post(`/vertex-data/${connectionId}`, data)
}

export function updateVertex(connectionId, vertexId, data) {
  return client.put(`/vertex-data/${connectionId}/vertex/${encodeURIComponent(vertexId)}`, data)
}

export function deleteVertex(connectionId, vertexId) {
  return client.delete(`/vertex-data/${connectionId}/vertex/${encodeURIComponent(vertexId)}`)
}

export function batchDeleteVertices(connectionId, ids) {
  return client.post(`/vertex-data/${connectionId}/batch-delete`, { ids })
}

export function clearVertices(connectionId, schema, label) {
  return client.post(`/vertex-data/${connectionId}/${schema}/${label}/clear`)
}

export function getGremlinExamples(schema, label) {
  return client.get(`/vertex-data/gremlin-examples/${schema}/${label}`)
}

export function exportVertices(connectionId, schema, label, format, filters = {}) {
  return client.get(`/vertex-data/${connectionId}/${schema}/${label}/export`, {
    params: { format, ...filters },
  })
}
