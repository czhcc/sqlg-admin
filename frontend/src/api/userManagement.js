import client from './client'

export function pageUsers(params) {
  return client.get('/user/management', { params })
}

export function getRoles() {
  return client.get('/user/management/roles')
}

export function getUserDetail(id) {
  return client.get(`/user/management/${id}`)
}

export function createUser(body) {
  return client.post('/user/management', body)
}

export function updateUser(id, body) {
  return client.put(`/user/management/${id}`, body)
}

export function updateUserStatus(id, status) {
  return client.put(`/user/management/${id}/status`, { status })
}

export function resetPassword(id, password) {
  return client.put(`/user/management/${id}/password-reset`, { password })
}

export function assignRoles(id, roles) {
  return client.put(`/user/management/${id}/roles`, { roles })
}

export function getPermissions(id) {
  return client.get(`/user/management/${id}/permissions`)
}

export function getConnectionPermissions(id) {
  return client.get(`/user/management/${id}/connections`)
}

export function getUserLoginLogs(id, params) {
  return client.get(`/user/management/${id}/login-logs`, { params })
}

export function getUserOperationLogs(id, params) {
  return client.get(`/user/management/${id}/operation-logs`, { params })
}

export function deleteUser(id) {
  return client.delete(`/user/management/${id}`)
}
