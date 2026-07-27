import client from './client'

export function listWorkspaceConnections() {
  return client.get('/workspace/connections')
}

export function setWorkspaceActiveConnection(connectionId) {
  return client.put('/workspace/active-connection', { connectionId })
}

export function getWorkspaceOverview(connectionId) {
  return client.get(`/workspace/overview/${connectionId}`)
}

export function getConnectionStatus() {
  return client.get('/workspace/connection-status')
}

export function getWorkspaceAlerts(connectionId) {
  return client.get(`/workspace/alerts/${connectionId}`)
}
