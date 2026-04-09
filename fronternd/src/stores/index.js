import { defineStore } from 'pinia'
import router from '@/router'
import { transformRoutes,buildMenuTree} from '@/utils/routeUtils'
import {MenuService} from '@/api/api'
import {apiRequest} from '@/api/axios'

export const useLoginStore = defineStore({
  id: 'login',
  state: () => ({
    isLogin: false,
    uuidLogin: '',
    token: '',
    menuMode: false,
    isDeep: false,
    topicColor: '#409eff',
  }),
  persist: {
    enabled: true, // 启用
    strategies: [
      // storage 可选localStorage或sessionStorage
      // paths 给指定数据持久化
      { key: 'isLogin', storage: localStorage },
      { key: 'uuidLogin', storage: localStorage },
      { key: 'token', storage: localStorage },
      { key: 'menuMode', storage: localStorage },
      { key: 'isDeep', storage: localStorage },
    ]
  },
})

export const useTitleStore = defineStore({
  id: 'title',
  state: () => ({
    title: '',
    env: '',
    caseId: '',
    caseName: '',
  }),
  persist: {
    enabled: true, // 启用
    strategies: [
      // storage 可选localStorage或sessionStorage
      // paths 给指定数据持久化
      { key: 'title', storage: localStorage },
    ]
  },
})

export const useUserStore = defineStore({
  id: 'user',
  state: () => ({
    user: {},
    isSys:false,
  }),
  actions: {
		setUser (user) {
      this.isSys=user.type==1
			this.user = user;
		},
	},
  // 开启持久化
  persist: {
    enabled: true, // 启用
    strategies: [
      // storage 可选localStorage或sessionStorage
      // paths 给指定数据持久化
      { key: 'user', storage: localStorage }
    ]
  },
})


export const useMenuStore=defineStore({
  id:'menu',
  state: () => ({ menuList:[],  hasRoute: false, menuRenderList:[] }),
  getters: {},
  actions: {
    //获取菜单数据
    async setMenuList() {
      try {
        // 从接口获取菜单数据
        const  data = await apiRequest(MenuService.queryList)
        this.menuList =data
        this.menuRenderList=buildMenuTree(data)
      } catch (error) {
        console.log(error)
        this.menuList=[]
        this.menuRenderList=[]
        this.hasRoute=false
      }
    },
    // 注册动态路由
    registerRoutes() {
      if(this.hasRoute) return true
      transformRoutes( this.menuList.filter(item=>item.url!='other') ).forEach((route,i)=>{
        if(router.hasRoute(route.name)){
          router.removeRoute(route.name)
        }
        router.addRoute('Layout',route)
      })
      this.hasRoute=true
    },
    // 获取第一个有效菜单 (若存在其他菜单，过滤掉)
    getFirstMenu() {
      const validMenus = this.menuList.filter(menu => menu.is_hidden === 0&&menu.url!='other').sort((a, b) => a.order_num - b.order_num)
      if(validMenus.length>0){
        return "/"+validMenus[0].url
      }else {
        return '/login'
      }
    },

    resetRouter() {
      router.getRoutes().forEach(route => {
        if (route.name && !['Layout', 'Login'].includes(route.name)) {
          router.removeRoute(route.name)
        }
      })
      this.hasRoute=false
    }
  },
})


