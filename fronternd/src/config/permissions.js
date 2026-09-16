// 保留旧账号含义：1 平台管理员、2 基线维护者、3 实验操作员；4 为改进开发者。
export const ROLE_DEFINITIONS = Object.freeze({
  1: { code: 'PlatformAdmin', name: '平台管理员', permissions: ['在个人中心查看用户列表，修改其他用户的权限等级', '平台操作与跨层集成', '平台配置、Runner 与引擎适配通过 GitHub 协作维护'] },
  2: { code: 'BaselineMaintainer', name: '基线维护者', permissions: ['数据集准备、训练任务与结果管理', '基线定义、源码与默认参数通过 GitHub PR 审查维护'] },
  3: { code: 'ExperimentOperator', name: '实验操作员', permissions: ['查询和预览已有数据集', '选择已开放基线与改进包，调整允许的实验参数', '创建、编辑和运行训练任务，查看结果、日志与配置'] },
  4: { code: 'ImprovementDeveloper', name: '改进开发者', permissions: ['导入、修改、删除数据集，维护标签映射与预处理脚本', '创建实例数据集、调整训测划分', '训练任务与结果管理', '改进包定义、模块源码和参数通过 GitHub PR 审查维护'] },
})

export function getRole(user = {}) {
  return ROLE_DEFINITIONS[Number(user.type)] || { code: 'Unknown', name: '未分配权限', permissions: ['请联系维护人员分配固定角色'] }
}

export function canMaintain(user = {}) {
  return [1, 2, 4].includes(Number(user.type))
}

const EXPERIMENT_PAGES = new Set(['originalDatasetManage', 'taskDatabaseManage', 'taskDatasetbaseManage', 'preprocess', 'intanceDatabase', 'trainTask', 'resultQuery', 'datasetManageUnified', 'userProfile'])
export function canVisitPage(user = {}, page = '') {
  return canMaintain(user) || EXPERIMENT_PAGES.has(page.replace(/^\//, '').split('/')[0])
}

// 前端请求兜底，覆盖旧页面入口；后端接口权限仍需后续单独实施。
export function isMaintenanceRequest(url = '', method = 'get') {
  const path = url.split('?')[0].replace(/^\/develop/, '').replace(/\/$/, '')
  const verb = method.toLowerCase()
  if (verb === 'get') return false
  return /^\/(original-dataset|taskDatasetDev|taskDataset|instance|instanceDataset|preprocess|engineProject|engineTask|trainScript|trainLabel|profile_train|profile_trans|menu|user|userProject|files)(\/|$)/.test(path)
    && (verb === 'delete' || /\/(add|update|delete|del|save|import|mark-subsets|mapping|export|clear|upload|upload-template|merge-target|merge-pretrain|splitTrainTest|reset)(\/|$)/.test(path) || path === '/taskDatasetDev/tasks' || path === '/instance/instancedatasets/create')
    || /^\/api\/(preprocess\/run|template\/upload|runner\/(start|restart|dependencies\/install))$/.test(path)
    || /^\/trainResult\/(del(?:\/batch)?|meta\/update)$/.test(path)
}
