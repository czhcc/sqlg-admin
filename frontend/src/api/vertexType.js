import client from './client'

export function listVertexTypeConnections() {
  return client.get('/vertex-type/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/vertex-type/active-connection', { connectionId })
}

export function listVertexTypes(connectionId) {
  return client.get(`/vertex-type/${connectionId}`)
}

export function getVertexTypeDetail(connectionId, schema, label) {
  return client.get(`/vertex-type/${connectionId}/${schema}/${label}`)
}

export function createVertexType(connectionId, data) {
  return client.post(`/vertex-type/${connectionId}`, data)
}

export function updateVertexType(connectionId, data) {
  return client.put(`/vertex-type/${connectionId}`, data)
}

export function clearVertices(connectionId, schema, label) {
  return client.post(`/vertex-type/${connectionId}/clear-vertices`, { schema, label })
}

export function deleteVertexType(connectionId, schema, label) {
  return client.delete(`/vertex-type/${connectionId}`, { data: { schema, label } })
}

export function getRelatedEdges(connectionId, schema, label) {
  return client.get(`/vertex-type/${connectionId}/${schema}/${label}/edges`)
}

export function getTableSchema(connectionId, schema, label) {
  return client.get(`/vertex-type/${connectionId}/${schema}/${label}/table-schema`)
}

export function getGremlinExamples(schema, label) {
  return client.get(`/vertex-type/gremlin-examples/${schema}/${label}`)
}

export function getSqlExamples(schema, label) {
  return client.get(`/vertex-type/sql-examples/${schema}/${label}`)
}
