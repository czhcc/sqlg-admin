import client from './client'

export function listGraphExploreConnections() {
  return client.get('/graph-explore/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/graph-explore/active-connection', { connectionId })
}

export function getSchemas(connectionId) {
  return client.get(`/graph-explore/${connectionId}/schemas`)
}

export function getVertexLabelProperties(connectionId, schema, label) {
  return client.get(`/graph-explore/${connectionId}/${schema}/${label}/properties`)
}

export function searchVertices(connectionId, schema, label, params) {
  return client.get(`/graph-explore/${connectionId}/${schema}/${label}/search`, { params })
}

export function expandNeighbors(connectionId, payload) {
  return client.post(`/graph-explore/${connectionId}/expand`, payload)
}

export function refreshConnection(connectionId) {
  return client.post(`/graph-explore/${connectionId}/refresh`)
}
