import client from './client'

export function listTopologyConnections() {
  return client.get('/topology/connections')
}

export function getTopology(connectionId) {
  return client.get(`/topology/${connectionId}`)
}

export function refreshTopology(connectionId) {
  return client.post(`/topology/${connectionId}/refresh`)
}

export function setActiveConnection(connectionId) {
  return client.put('/topology/active-connection', { connectionId })
}

