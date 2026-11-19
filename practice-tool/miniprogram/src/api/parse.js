import { request } from './request'

export const createTask = (payload) => request({ url: '/parse/tasks', method: 'POST', data: payload })
export const getTask = (taskId) => request({ url: `/parse/tasks/${taskId}` })
export const getTaskPreview = (taskId, page = 0, size = 50) =>
  request({ url: `/parse/tasks/${taskId}/preview?page=${page}&size=${size}` })
export const cancelTask = (taskId) => request({ url: `/parse/tasks/${taskId}`, method: 'DELETE' })
export const commitTask = (taskId, payload) =>
  request({ url: `/parse/tasks/${taskId}/commit`, method: 'POST', data: payload })
export const patchQuestion = (taskId, idx, override) =>
  request({ url: `/parse/tasks/${taskId}/questions/${idx}`, method: 'PATCH', data: override })
