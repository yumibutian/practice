import { request } from './request'

export const startPractice = (payload) =>
  request({ url: '/practice/start', method: 'POST', data: payload })
export const submitPractice = (recordId, answers) =>
  request({ url: `/practice/${recordId}/submit`, method: 'POST', data: { answers } })


export const interruptPractice = (recordId, progress) =>
  request({ url: `/practice/${recordId}/interrupt`, method: 'POST', data: { progress } })
