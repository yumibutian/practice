import { request } from './request'

export const listWrong = (params = {}) => {
  const qs = Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
  return request({ url: `wrong-book${qs ? '?' + qs : ''}` })
}
