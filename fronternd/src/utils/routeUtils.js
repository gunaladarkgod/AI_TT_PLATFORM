
// 将后端数据转换为路由格式
export const transformRoutes = (menuData) => {
    const modules = import.meta.glob('@/views/**/index.vue') // 匹配 views 目录下的组件
    return menuData.sort((a, b) => a.order_num - b.order_num) // 按排序号排序
      .map(item => {
        // 顶部配置可直接指定 Vue 文件；后台旧菜单仍按 url/index.vue 自动映射。
        const componentFile = item.component || `${item.url}/index.vue`
        const componentKey = componentFile.startsWith('/src/views/')
          ? componentFile
          : `/src/views/${componentFile}`
        const oldComponentKey = `/src/views/old_views/${componentFile}`
        const component = modules[componentKey] || modules[oldComponentKey]
        if (!component) {
          console.error(`[route] 找不到页面组件：${componentKey} 或 ${oldComponentKey}（route=${item.url}）`)
        }
        return {
          path: `/${item.url}`,
          name: item.url,
          component,
          meta: {
            title: item.name,
            order: item.order_num,
            isHidden: item.is_hidden,
            componentFile,
          }
        }
      })
}



export const buildMenuTree=(menuList)=> {
    // 1. 筛选一级菜单并排序
    const topMenus = menuList
      .filter(item => item.parent_id == 0)
      .sort((a, b) => a.order_num - b.order_num);
  
    // 2. 挂载二级菜单
    topMenus.forEach(parent => {
      parent.children = menuList
        .filter(child => child.parent_id == parent.id)
        .sort((a, b) => a.order_num - b.order_num);
    });
  
    return topMenus;
  }

