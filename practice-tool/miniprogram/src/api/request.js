import config from '../config/dev.js'

let token = ''
let tokenName = 'satoken'

export function setAuth(t, name) {
  token = t
  tokenName = name || tokenName
  uni.setStorageSync('quiz_token', t)
  uni.setStorageSync('quiz_tokenName', tokenName)
}

export function restoreAuth() {
  token = uni.getStorageSync('quiz_token') || ''
  tokenName = uni.getStorageSync('quiz_tokenName') || 'satoken'
}

export function clearAuth() {
  token = ''
  uni.removeStorageSync('quiz_token')
  uni.removeStorageSync('quiz_tokenName')
}

export function hasAuth() { return !!token }


export function persistAuth() {
  if (!token) return false
  uni.setStorageSync('quiz_token', token)
  uni.setStorageSync('quiz_tokenName', tokenName)
  return true
}


export function authHeader() { return { [tokenName]: token } }

export function isBizError(e) { return e && typeof e.code === 'string' && e.code !== 'NETWORK' }

export function unwrap(body) {
  if (!body || typeof body !== 'object' || !('code' in body)) {
    throw new Error('响应格式异常')
  }
  if (body.code !== 'OK') {
    const err = new Error(body.message || '请求失败')
    err.code = body.code
    throw err
  }
  return body.data
}

export function request({ url, method = 'GET', data, header = {} }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: config.baseUrl + url,
      method,
      data,
      header: { 'Content-Type': 'application/json', ...authHeader(), ...header },
      success: (res) => {
        try { resolve(unwrap(res.data)) } catch (e) { reject(e) }
      },
      fail: () => {
        const err = new Error('网络异常，请稍后重试')
        err.code = 'NETWORK'
        reject(err)
      }
    })
  })
}

export function toastError(e) {
  uni.showToast({ title: e && e.message ? e.message : '操作失败', icon: 'none' })
}
