import { request } from './request'

export const listBanks = () => request({ url: 'banks' })
export const bankDetail = (bankId) => request({ url: ` banks/${bankId}` })
export const renameBank = (bankId, name) =>
  request({ url: `banks/${bankId}/name`, method: 'PATCH', data: { name } })
export const pinBank = (bankId, pinned) =>
  request({ url: `banks/${bankId}/pin`, method: 'PATCH', data: { pinned } })
export const deleteBank = (bankId) => request({ url: ` banks/${bankId}`, method: 'DELETE' })
export const bankQuestions = (bankId, page = 0, size = 20) =>
  request({ url: `banks/${bankId}/questions?page=${page}&size=${size}` })
