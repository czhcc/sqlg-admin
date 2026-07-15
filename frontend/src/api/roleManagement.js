import client from './client'

export function pageRoles(params) {
  return client.get('/role/management', { params })
}

export function getRoleCatalog() {
  return client.get('/role/management/catalog')
}

export function getRoleDetail(id) {
  return client.get(`/role/management/${id}`)
}

export function updateRoleBasic(id, body) {
  return client.put(`/role/management/${id}/basic`, body)
}

export function updateMenuPermissions(id, menus) {
  return client.put(`/role/management/${id}/menus`, { menus })
}

export function updateOperationPermissions(id, operations) {
  return client.put(`/role/management/${id}/operations`, { operations })
}

export function updateGremlinPermission(id, level) {
  return client.put(`/role/management/${id}/gremlin`, { level })
}

export function updateDangerousPermissions(id, operations) {
  return client.put(`/role/management/${id}/dangerous`, { operations })
}

export function getConnectionAuth(id) {
  return client.get(`/role/management/${id}/connections`)
}

export function updateConnectionDefault(id, defaultLevel) {
  return client.put(`/role/management/${id}/connections/default`, { default: defaultLevel })
}

export function updateConnectionAuth(id, connectionId, accessLevel) {
  return client.put(`/role/management/${id}/connections/${connectionId}`, { accessLevel })
}

export function getRoleMembers(id) {
  return client.get(`/role/management/${id}/members`)
}

export function addRoleMembers(id, userIds) {
  return client.post(`/role/management/${id}/members`, { userIds })
}

export function removeRoleMember(id, userId) {
  return client.delete(`/role/management/${id}/members/${userId}`)
}

export function deleteRole(id) {
  return client.delete(`/role/management/${id}`)
}
