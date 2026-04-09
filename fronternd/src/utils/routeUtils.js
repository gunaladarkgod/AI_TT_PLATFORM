
// 将后端数据转换为路由格式
export const transformRoutes = (menuData) => {
    const modules = import.meta.glob('@/views/**/index.vue') // 匹配 views 目录下的组件
    return menuData.sort((a, b) => a.order_num - b.order_num) // 按排序号排序
      .map(item => ({
        path: `/${item.url}`,
        name: item.url,
        component: modules[`/src/views/${item.url}/index.vue`], // 动态映射组件路径
        meta: {
          title: item.name,
          order: item.order_num,
          isHidden:item.is_hidden
        }
    }))
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
