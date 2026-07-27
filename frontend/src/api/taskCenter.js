import client from './client'

export function listTasks(params) {
  return client.get('/task', { params })
}

export function getTaskDetail(id) {
  return client.get(`/task/${id}`)
}

export function createTask(data) {
  return client.post('/task', data)
}

export function retryTask(id) {
  return client.post(`/task/${id}/retry`)
}

export function cancelTask(id) {
  return client.post(`/task/${id}/cancel`)
}

export function deleteTask(id) {
  return client.delete(`/task/${id}`)
}

export function downloadTaskResult(id, fileType = 'result') {
  return client.get(`/task/${id}/download`, {
    params: { fileType },
    responseType: 'blob',
  })
}

export function getTaskTypes() {
  return client.get('/task/types')
}
