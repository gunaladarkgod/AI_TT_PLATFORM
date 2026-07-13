/**
 * 顶部 Tab 布局的唯一配置入口。
 *
 * 每项三个核心字段：
 * - label：Tab 显示名称；
 * - route：浏览器路由名称（最终访问 /route）；
 * - component：相对于 src/views 的 Vue 文件路径。
 *
 * 新增页面示例：
 * 1. 创建 src/views/myNewPage/index.vue；
 * 2. 在数组中加入：
 *    { label: '新页面', route: 'myNewPage', component: 'myNewPage/index.vue' }
 * 调整顺序只需移动对象；未列出的后台菜单会自动进入“...”。
 */
export const TOP_NAV_LAYOUT = Object.freeze([
  {
    label: '原始数据集',
    route: 'originalDatasetManage',
    component: 'originalDatasetManage/index.vue',
  },
  {
    label: '任务管理',
    route: 'taskDatabaseManage',
    component: 'taskDatabaseManage/index.vue',
    aliases: ['taskDatasetbaseManage'], // 兼容数据库中的旧路由名
  },
  {
    label: '实例数据集预处理',
    route: 'preprocess',
    component: 'preprocess/index.vue',
  },
  {
    label: '实例数据集',
    route: 'intanceDatabase',
    component: 'intanceDatabase/index.vue',
  },
  {
    label: '模型训练',
    route: 'trainTask',
    component: 'trainTask/index.vue',
  },
  {
    label: '结果查询',
    route: 'resultQuery',
    component: 'resultQuery/index.vue',
  },
  {
    label: '数据集管理（dev）',
    route: 'datasetManageUnified',
    component: 'datasetManageUnified/index.vue',
  },
])

export const TOP_NAV_MORE_LABEL = '...'

const SYNTHETIC_MENU_ID_BASE = 99000

/** 保证配置中的入口存在于动态菜单中，从而可以正常注册对应页面路由。 */
export function ensureTopNavigationMenus(menuList = []) {
  const source = Array.isArray(menuList) ? menuList : []
  const configured = new Map()
  TOP_NAV_LAYOUT.forEach((item, index) => {
    const nav = { ...item, index }
    configured.set(item.route, nav)
    ;(item.aliases || []).forEach(alias => configured.set(alias, nav))
  })

  const normalized = source.map((item) => {
    const nav = configured.get(item?.url)
    if (!nav) return item
    return {
      ...item,
      url: nav.route,
      name: nav.label,
      component: nav.component,
      order_num: nav.index + 1,
      is_hidden: 0,
      parent_id: 0,
    }
  })

  const existingRoutes = new Set(normalized.map(item => item?.url).filter(Boolean))

  TOP_NAV_LAYOUT.forEach((nav, index) => {
    if (existingRoutes.has(nav.route)) return
    normalized.push({
      id: SYNTHETIC_MENU_ID_BASE + index,
      url: nav.route,
      name: nav.label,
      component: nav.component,
      order_num: index + 1,
      is_hidden: 0,
      parent_id: 0,
    })
  })
  return normalized
}

/** 将完整菜单拆成顶部固定项和“...”中的其余项。 */
export function buildTopNavigation(menuTree = []) {
  const visibleMenus = (Array.isArray(menuTree) ? menuTree : [])
    .filter(item => item && Number(item.is_hidden || 0) !== 1)
  const byUrl = new Map(visibleMenus.map(item => [item.url, item]))
  const primaryUrls = new Set(TOP_NAV_LAYOUT.map(item => item.route))

  const primary = TOP_NAV_LAYOUT
    .map(nav => {
      const menu = byUrl.get(nav.route)
      return menu ? { ...menu, name: nav.label, children: menu.children || [] } : null
    })
    .filter(Boolean)

  const overflow = visibleMenus.filter(item => !primaryUrls.has(item.url))
  return { primary, overflow }
}
