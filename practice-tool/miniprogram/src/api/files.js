import { request, authHeader } from './request'
import config from '../config/dev.js'

export function initFile({ fileName, fileSize, fileHash, ext }) {
  return request({ url: '/files/init', method: 'POST', data: { fileName, fileSize, fileHash, ext } })
}

export function uploadFile(filePath, fileId) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: config.baseUrl + `/files/${fileId}/upload`,
      filePath,
      name: 'file',
      // 后端 /api/** 全量挂 AuthInterceptor，缺 token 必然 UNAUTHORIZED
      header: authHeader(),
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code !== 'OK') {
            const err = new Error(body.message || '上传失败')
            err.code = body.code
            reject(err)
          } else { resolve(body.data) }
        } catch (e) { reject(new Error('上传响应解析失败')) }
      },
      fail: () => reject(new Error('网络异常，上传失败'))
    })
  })
}
