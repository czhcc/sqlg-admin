import client from './client'

export function login(username, password) {
  return client.post('/auth/login', { username, password })
}

export function logout() {
  return client.post('/auth/logout')
}

export function getUserInfo() {
  return client.get('/auth/info')
}

export function getMyPermissions() {
  return client.get('/auth/permissions')
}

export function updateProfile(body) {
  return client.put('/auth/profile', body)
}

export function changePassword(body) {
  return client.put('/auth/password', body)
}
