import client from './client'

export function listEdgeTypeConnections() {
  return client.get('/edge-type/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/edge-type/active-connection', { connectionId })
}

export function listEdgeTypes(connectionId) {
  return client.get(`/edge-type/${connectionId}`)
}

export function getEdgeTypeDetail(connectionId, schema, label) {
  return client.get(`/edge-type/${connectionId}/${schema}/${label}`)
}

export function createEdgeType(connectionId, data) {
  return client.post(`/edge-type/${connectionId}`, data)
}

export function deleteEdgeType(connectionId, schema, label) {
  return client.delete(`/edge-type/${connectionId}`, { data: { schema, label } })
}

export function clearEdges(connectionId, schema, label) {
  return client.post(`/edge-type/${connectionId}/clear-edges`, { schema, label })
}

export function getTableSchema(connectionId, schema, label) {
  return client.get(`/edge-type/${connectionId}/${schema}/${label}/table-schema`)
}

export function listVertexLabels(connectionId) {
  return client.get(`/edge-type/${connectionId}/vertex-labels`)
}

export function getGremlinExamples(schema, label) {
  return client.get(`/edge-type/gremlin-examples/${schema}/${label}`)
}

export function getSqlExamples(schema, label) {
  return client.get(`/edge-type/sql-examples/${schema}/${label}`)
}
