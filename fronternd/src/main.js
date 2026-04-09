import { createApp } from 'vue'

import App from './App.vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'





import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import router from './router'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './assets/iconfont/iconfont.css'
// 注意加载顺序
import './style.css'
//全局注册自定义指令
import elHeightAdaptiveTable from './directives/el-table-adaptive'
//vxe ui插件引入
import VxeUIAll from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeUITable from 'vxe-table'
import 'vxe-table/lib/style.css'



const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.use(createPinia().use(piniaPluginPersistedstate))

app.directive('elHeightAdaptiveTable', elHeightAdaptiveTable) //表格高度自定义指令
app.use(router).use(VxeUIAll).use(VxeUITable)
app.use(ElementPlus, { locale: zhCn, })
app.mount("#app")





