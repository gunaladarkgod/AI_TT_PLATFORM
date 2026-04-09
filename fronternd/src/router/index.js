import { createRouter, createWebHistory,createWebHashHistory } from 'vue-router'

import { useLoginStore ,useUserStore,useMenuStore} from '@/stores/index'
import Layout from '../components/layout/index.vue'
import Login from '../views/login.vue'
import EngineProject from '../views/engineProject/index.vue'

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