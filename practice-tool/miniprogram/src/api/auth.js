import {request, setAuth, clearAuth} from './request'

export async function devLogin(userId) {
    const data = await request({url: 'auth/dev-login', method: 'POST', data: {userId}})
    setAuth(data.token, data.tokenName)
    return data
}


export function fetchMe() {
    return request({url: 'auth/me'})
}

export function logout() {
    clearAuth()
}
