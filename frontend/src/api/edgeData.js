import client from './client'

export function listEdgeDataConnections() {
  return client.get('/edge-data/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/edge-data/active-connection', { connectionId })
}

export function getTree(connectionId) {
  return client.get(`/edge-data/${connectionId}/tree`)
}

export function refreshTree(connectionId) {
  return client.post(`/edge-data/${connectionId}/refresh`)
}

export function getEdgeLabelProperties(connectionId, schema, label) {
  return client.get(`/edge-data/${connectionId}/${schema}/${label}/properties`)
}

export function pageEdges(connectionId, schema, label, params) {
  return client.get(`/edge-data/${connectionId}/${schema}/${label}`, { params })
}

export function getEdgeDetail(connectionId, edgeId) {
  return client.get(`/edge-data/${connectionId}/edge/${encodeURIComponent(edgeId)}`)
}

export function createEdge(connectionId, data) {
  return client.post(`/edge-data/${connectionId}`, data)
}

export function updateEdge(connectionId, edgeId, data) {
  return client.put(`/edge-data/${connectionId}/edge/${encodeURIComponent(edgeId)}`, data)
}

export function deleteEdge(connectionId, edgeId) {
  return client.delete(`/edge-data/${connectionId}/edge/${encodeURIComponent(edgeId)}`)
}

export function batchDeleteEdges(connectionId, ids) {
  return client.post(`/edge-data/${connectionId}/batch-delete`, { ids })
}

export function clearEdges(connectionId, schema, label) {
  return client.post(`/edge-data/${connectionId}/${schema}/${label}/clear`)
}

export function getEdgeGremlinExamples(schema, label) {
  return client.get(`/edge-data/gremlin-examples/${schema}/${label}`)
}

export function exportEdges(connectionId, schema, label, format, filters = {}) {
  return client.get(`/edge-data/${connectionId}/${schema}/${label}/export`, {
    params: { format, ...filters },
  })
}

export function searchVertices(connectionId, schema, label, params) {
  return client.get(`/edge-data/${connectionId}/vertices/${schema}/${label}`, { params })
}

export function getEdgeVertexLabels(connectionId, schema, label) {
  return client.get(`/edge-data/${connectionId}/${schema}/${label}/vertex-labels`)
}
