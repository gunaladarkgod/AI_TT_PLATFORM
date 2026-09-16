import test from 'node:test'
import assert from 'node:assert/strict'
import { ROLE_OPTIONS, getRole, canMaintain, canVisitPage, isMaintenanceRequest } from './permissions.js'

test('保留旧普通用户为实验操作员，新增改进开发者，未知角色不能维护', () => {
  assert.equal(getRole({ type: 3 }).code, 'ExperimentOperator')
  assert.equal(getRole({ type: 4 }).code, 'ImprovementDeveloper')
  for (const type of [1, 2, 4]) assert.equal(canMaintain({ type }), true)
  for (const type of [3, 0, undefined]) assert.equal(canMaintain({ type }), false)
})
test('实验操作员可使用主流程和个人中心，不能访问旧维护页面', () => {
  for (const page of ['/originalDatasetManage', '/trainTask', '/resultQuery', '/userProfile']) assert.equal(canVisitPage({ type: 3 }, page), true)
  for (const page of ['/user', '/menu', '/taskDatabaseManageOld', '/engineProject']) assert.equal(canVisitPage({ type: 3 }, page), false)
  assert.equal(canVisitPage({ type: 4 }, '/taskDatabaseManageOld'), true)
})
test('维护请求被识别，POST 查询、训练和个人资料保存保持可用', () => {
  for (const path of ['/original-dataset/external/import', '/original-dataset/external/delete', '/taskDatasetDev/tasks', '/taskDatasetDev/tasks/mapping', '/taskDatasetDev/tasks/export', '/taskDatasetDev/tasks/clear', '/preprocess/upload', '/api/preprocess/run', '/instance/instancedatasets/splitTrainTest', '/instance/trainTestSplit/save', '/trainResult/del/batch', '/trainResult/meta/update', '/api/runner/start']) assert.equal(isMaintenanceRequest(path, 'post'), true, path)
  assert.equal(isMaintenanceRequest('/instance/instancedatasets/1', 'delete'), true)
  for (const path of ['/auth/profile', '/auth/login', '/menu/list', '/original-dataset/sample-random', '/taskDatasetDev/tasks/list', '/taskDatasetDev/tasks/open-path', '/instance/instancedatasets', '/instance/getTrainableNames', '/instance/instancedatasets/trainingReadiness', '/trainTask/add', '/trainTask/update', '/trainTask/enqueue', '/trainTask/stop', '/trainResult/all', '/trainResult/detail', '/api/research/stack/resolve']) assert.equal(isMaintenanceRequest(path, 'post'), false, path)
})

// 显示顺序与历史数据库类型值独立，避免保存时交换两类账号权限。
test('权限编号从高到低排列，等级选项保留真实数据库类型值', () => {
  assert.deepEqual(ROLE_OPTIONS.map(role => [role.type, role.name]), [
    [1, '1. 平台管理员'],
    [2, '2. 基线维护者'],
    [4, '3. 改进开发者'],
    [3, '4. 实验操作员'],
  ])
})
