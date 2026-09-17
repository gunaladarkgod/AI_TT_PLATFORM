import { createRouter, createWebHistory,createWebHashHistory } from 'vue-router'

import { useLoginStore ,useUserStore,useMenuStore} from '@/stores/index'
import Layout from '../components/layout/index.vue'
import Login from '../views/login.vue'
import EngineProject from '../views/old_views/engineProject/index.vue'
import TaskDatasetManageDev from '../views/old_views/taskDatasetManageDev/index.vue'
import DatasetManageUnified from '../views/datasetManageUnified/index.vue'
import { canVisitPage } from '@/config/permissions'

const routes=[
    {
      path: '/login',
      name: 'Login',
      component: Login,
      meta: { title: '用户登录' }
    },
    {
      path:'/layout',
      name:'Layout',
      component:Layout,
      children: [
        { path: '/userProfile', name: 'UserProfile', component: () => import('@/views/userProfile/index.vue'), meta: { title: '个人中心' } },
        {
          path: '/taskDatasetManageDev',
          name: 'taskDatasetManageDev',
          component: TaskDatasetManageDev,
          meta: { title: '任务数据集管理（dev）' }
        },
        {
          path: '/datasetManageUnified',
          name: 'datasetManageUnified',
          component: DatasetManageUnified,
          meta: { title: '数据集管理（dev）' }
        }
      ]
    },
    {
      path: '/', redirect: '/login'
    },
    {
      path: '/engineProject/:taskName?',
      name: 'engineProject',
      component: EngineProject,
      props: true
    }

]

/* 初始化配置 */ 
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes:routes
})




router.beforeEach(async (to, from) => { // 使用return替代next参数
  const loginStore = useLoginStore()
  const menuStore = useMenuStore()
  //  处理登录页特殊逻辑
  if (to.path.toLowerCase() === '/login') {
    if(!loginStore.token){
      // 无token时重置路由（仅清除动态路由）
      menuStore.resetRouter()
    } 
    return true
  }

  
  const whiteList = ['/login']
  if (whiteList.includes(to.path)) return true
  if (!loginStore.token) {
    return {  path: '/login', query: { redirect: to.fullPath }, replace: true }
  }
  if (!canVisitPage(useUserStore().user, to.path)) return '/trainTask'
  if(menuStore.hasRoute) return true
  try {
    if (!menuStore.menuList.length) {
      await menuStore.setMenuList() // 必须await等待请求完成
    }
    await menuStore.registerRoutes()
    return to.fullPath
  } catch (error) {
    console.error('路由初始化失败:', error)
    useLoginStore().$patch((state) => {
      state.isLogin = false;
      state.uuidLogin = "";
      state.token = "";
    });
    return '/login'
  }
})




export default router
