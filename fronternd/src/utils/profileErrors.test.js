import test from 'node:test'
import assert from 'node:assert/strict'
import { profileFailure } from './profileErrors.js'
test('失效凭据与权限不足提供明确恢复方式', () => {
  assert.match(profileFailure({ code: 2, msg: '认证失败' }), /重新登录/)
  assert.match(profileFailure({ code: 3 }), /没有用户权限管理权限/)
})
test('旧后端与网络失败不会被误报为无用户', () => {
  assert.match(profileFailure({ response: { status: 404 } }), /重启/)
  assert.match(profileFailure(new Error('Network Error')), /正确转发/)
})
