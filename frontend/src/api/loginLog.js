import client from './client'

export function pageLoginLogs(params) {
  return client.get('/login-log', { params })
}
