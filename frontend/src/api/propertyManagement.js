import client from './client'

export function listPropertyManagementConnections() {
  return client.get('/property-management/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/property-management/active-connection', { connectionId })
}

export function getTree(connectionId) {
  return client.get(`/property-management/${connectionId}/tree`)
}

export function refreshTree(connectionId) {
  return client.post(`/property-management/${connectionId}/refresh`)
}

export function listProperties(connectionId, kind, schema, label) {
  return client.get(`/property-management/${connectionId}/${kind}/${schema}/${label}`)
}

export function addProperty(connectionId, kind, schema, label, data) {
  return client.post(`/property-management/${connectionId}/${kind}/${schema}/${label}`, data)
}

export function updateProperty(connectionId, kind, schema, label, propertyName, data) {
  return client.put(`/property-management/${connectionId}/${kind}/${schema}/${label}/${propertyName}`, data)
}

export function removeProperty(connectionId, kind, schema, label, propertyName) {
  return client.delete(`/property-management/${connectionId}/${kind}/${schema}/${label}`, {
    data: { propertyName },
  })
}

export function createIndex(connectionId, kind, schema, label, propertyName, unique = false) {
  return client.post(`/property-management/${connectionId}/${kind}/${schema}/${label}/index`, {
    propertyName,
    unique,
  })
}

export function removeIndex(connectionId, kind, schema, label, propertyName) {
  return client.delete(`/property-management/${connectionId}/${kind}/${schema}/${label}/index`, {
    data: { propertyName },
  })
}

export function listPropertyTypes() {
  return client.get('/property-management/property-types')
}
