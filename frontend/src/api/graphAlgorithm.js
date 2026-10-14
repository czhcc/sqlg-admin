import client from './client'

export const listAlgorithmConnections = () => client.get('/graph-algorithm/connections')
export const getAlgorithmScope = id => client.get(`/graph-algorithm/${id}/scope`)
export const runAlgorithm = (id, params) => client.post(`/graph-algorithm/${id}/tasks`, params)
export const getAlgorithmTask = id => client.get(`/graph-algorithm/tasks/${id}`)
export const getAlgorithmResult = id => client.get(`/graph-algorithm/tasks/${id}/result`)
export const cancelAlgorithm = id => client.post(`/graph-algorithm/tasks/${id}/cancel`)
export const exportAlgorithm = id => client.get(`/graph-algorithm/tasks/${id}/export`, { responseType: 'blob' })
