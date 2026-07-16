import client from './client'

export function searchOverviewUsers(params) {
  return client.get('/permission/overview/users', { params })
}

export function getUserPermissionOverview(id) {
  return client.get(`/permission/overview/users/${id}`)
}
