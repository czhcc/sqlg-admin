import client from './client'

export function listGremlinConnections() {
  return client.get('/gremlin/connections')
}

export function setActiveConnection(connectionId) {
  return client.put('/gremlin/active-connection', { connectionId })
}

export function executeGremlin(connectionId, payload) {
  return client.post(`/gremlin/${connectionId}/execute`, payload, { timeout: 60000 })
}

export function getHistory(connectionId) {
  return client.get(`/gremlin/${connectionId}/history`)
}

export function deleteHistory(id) {
  return client.delete(`/gremlin/history/${id}`)
}

export function clearHistory(connectionId) {
  return client.delete(`/gremlin/${connectionId}/history`)
}

export function getFavorites() {
  return client.get('/gremlin/favorites')
}

export function addFavorite(data) {
  return client.post('/gremlin/favorites', data)
}

export function updateFavorite(id, data) {
  return client.put(`/gremlin/favorites/${id}`, data)
}

export function deleteFavorite(id) {
  return client.delete(`/gremlin/favorites/${id}`)
}

export function refreshConnection(connectionId) {
  return client.post(`/gremlin/${connectionId}/refresh`)
}
