import { request } from './request'

export const getReport = (recordId) => request({ url: `/practice/${recordId}/report` })
