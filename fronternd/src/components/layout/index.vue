<template>
  <el-container>
    <el-header class="flex-between header-layout">
      <div class="flex-between not-select">
        <el-space>
          <el-space>
            <el-icon class="font-size-24">
              <el-image :src="logoPath"></el-image>
            </el-icon>
            <el-text class="text-white font-size-20 text-weigh-500" truncated>AI训练平台</el-text></el-space>

            <el-menu  router  v-if="!menuMode"  class="custom-menu not-select" :ellipsis="false"
              mode="horizontal"
              :default-active="route.path"
            >
              <el-menu-item
                v-for="item in topNavigation.primary"
                :key="item.url"
                :index="'/' + item.url"
                class="custom-menu-item"
                :class="route.path === '/' + item.url ? 'my-active' : ''"
              >
                <el-icon><i class="iconfont" :class="'icon-' + item.url" style="font-size: 14px"></i></el-icon>
                <template #title>{{ item.name }}</template>
              </el-menu-item>

              <el-sub-menu v-if="topNavigation.overflow.length" index="/__top_nav_more__" class="top-nav-more">
                <template #title>{{ TOP_NAV_MORE_LABEL }}</template>
                <template v-for="item in topNavigation.overflow" :key="item.url">
                  <el-menu-item
                    v-if="!item.children || item.children.length === 0"
                    :index="'/' + item.url"
                    :class="route.path === '/' + item.url ? 'my-active' : ''"
                  >
                    <el-icon><i class="iconfont" :class="'icon-' + item.url" style="font-size: 14px"></i></el-icon>
                    <template #title>{{ item.name }}</template>
                  </el-menu-item>
                  <el-sub-menu v-else :index="'/__top_nav_more__/' + item.url">
                    <template #title>
                      <el-icon><i class="iconfont" :class="'icon-' + item.url" style="font-size: 14px"></i></el-icon>
                      {{ item.name }}
                    </template>
                    <el-menu-item
                      v-for="child in item.children"
                      :key="child.url"
                      :index="'/' + child.url"
                      :class="route.path === '/' + child.url ? 'my-active' : ''"
                    >
                      {{ child.name }}
                    </el-menu-item>
                  </el-sub-menu>
                </template>
              </el-sub-menu>
            </el-menu>



        </el-space>
      </div>
      <div class="flex-end">
        <el-space :size="24">
          <el-dropdown class="flex-center">
            <div class="userBox">
              <el-avatar :size="30" class="avatar-user"></el-avatar>
              <span>{{ userStore.user?.username }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="changTopic"><el-icon
                    class="iconfont icon-yunhanghuanjingbushu fontSpan"></el-icon>切换主题</el-dropdown-item>
                <el-dropdown-item @click="showPwdModal">
                  <el-icon class="iconfont icon-xiugaimima fontSpan"></el-icon>修改密码</el-dropdown-item>
                <el-dropdown-item @click="logout"><el-icon
                    class="iconfont icon-pullleft fontSpan"></el-icon>退出登录</el-dropdown-item>

              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-space>
      </div>
    </el-header>
    <el-container class="body-layout">
      <el-aside class="not-select" width="auto" v-if="menuMode" style="min-width: 150px;">
        <!-- <el-menu router :collapse="isCollapse" :collapse-transition="false" mode="vertical">
          <template v-for="item in menuStore.menuList">
            <el-menu-item :index="'/'+item.url" v-if="!item.is_hidden" :key="item.id" class="custom-menu-item-v"   :class="route.path == '/'+item.url ? 'my-active' : ''">
              <el-icon><i class="iconfont icon-biaoqian" style="font-size: 14px"></i></el-icon>
              <template #title>{{ item.name }}</template>
            </el-menu-item>
          </template>
        </el-menu> -->



        <el-menu  router :collapse="isCollapse" :collapse-transition="false" mode="vertical"
              :default-active="route.path"
            >

              <template v-for="item in menuStore.menuRenderList">
                <template  v-if="!item.children.length>0">
                  <el-menu-item :index="'/'+item.url" :key="item.id" class="custom-menu-item-v"  :class="route.path == '/'+item.url ? 'my-active' : ''">
                    <el-icon><i class="iconfont"  :class="'icon-'+ item.url"  style="font-size: 14px"></i></el-icon>
                    <template #title>{{ item.name }}</template>
                  </el-menu-item>
              
                </template>
                <template v-else>
                  <el-sub-menu :index="'/'+item.url" :key="item.id">
                  
                    <template #title>  <el-icon><i class="iconfont"  :class="'icon-'+ item.url"   style="font-size: 14px"></i></el-icon>{{ item.name }}</template>
                    <el-menu-item v-for="val in item.children" :index="'/'+val.url" :key="val.id"   class="custom-menu-item-v"   :class="route.path == '/'+val.url ? 'my-active' : ''">
                      <el-icon><i class="iconfont" :class="'icon-'+ val.url"  style="font-size: 14px"></i></el-icon>
                      <template #title>{{ val.name }}</template>
                    </el-menu-item>
                    
                  </el-sub-menu>
                </template>
              </template>
            </el-menu>






      </el-aside>
      <el-container class="right-container">
        <el-header v-if="showPageTitleBar" class="flex-start main-header">
          <el-space>
            <el-icon class="iconfont text-grey" :class="menuMode ? 'icon-layout-5-fill' : 'icon-layout-top-fill'"
              @click="changeMenuMode"></el-icon>
            <el-text class="text-weigh-800" size="large">{{
              titleStore.title
            }}</el-text>
          </el-space></el-header>
        <el-main :class="['page-content', unifiedRouteClass, { 'page-content--unified': showUnifiedPageShell }]">
          <div v-if="showUnifiedPageShell" :class="['unified-page-shell', unifiedRouteShellClass]">
            <div class="unified-page-title-row">
              <h1 class="unified-page-title">{{ unifiedPageTitle }}</h1>
            </div>
            <div :class="['unified-page-card', unifiedRouteCardClass]">
              <router-view :msg="msgTime" />
            </div>
          </div>
          <router-view v-else :msg="msgTime" />
        </el-main>
        <!-- <el-footer class="flex-center main-footer"
          ><el-text class="foot-text">版权所有</el-text></el-footer
        > -->
      </el-container>
    </el-container>
  </el-container>
  <el-dialog v-model="pwdVisible" width="40%" title="修改密码" draggable :close-on-click-modal="false">
    <div>
      <el-form :model="pwdForm" ref="formRef" :rules="rules">
        <el-form-item label="请输入旧密码" prop="pwd1" required>
          <el-input v-model="pwdForm.pwd1" type="password" show-password clearable />
        </el-form-item>
        <el-form-item label="请输入新密码" prop="pwd2" required>
          <el-input v-model="pwdForm.pwd2" type="password" show-password clearable />
        </el-form-item>
        <el-form-item label="请确认新密码" prop="pwd3" required>
          <el-input v-model="pwdForm.pwd3" type="password" show-password clearable />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="updatePwd">确定</el-button>
      </span>
    </template>
  </el-dialog>
  <el-dialog v-model="colorVisible" width="400" title="切换主题色">
    <div class="flex-between">
      <el-color-picker v-model="colorTopic" :predefine="predefineColors" @change="onChangeColor" show-alpha />
      <el-button @click="colorVisible = false" size="small">关闭</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { ref, reactive, onMounted, onUnmounted, nextTick, onBeforeUnmount, watch } from "vue";
import { useRouter ,useRoute} from "vue-router";
import { AuthService, UserService } from "../../api/api";
import { useLoginStore, useTitleStore, useUserStore,useMenuStore } from "../../stores/index";
import md5 from "js-md5";
import { logoPath } from '../../api/axios'

import {setPrimaryColor} from '../../utils/color'
import { computed } from "vue";
import { buildTopNavigation, TOP_NAV_MORE_LABEL } from '@/config/topNavigation'
const menuStore=useMenuStore()
const route=useRoute()
const titleStore = useTitleStore();
const loginStore = useLoginStore();
const userStore = useUserStore();
const isCollapse = ref(false);
let timer = null
const menuMode = ref(loginStore.menuMode);//'vertical'  'horizontal'
const topNavigation = computed(() => buildTopNavigation(menuStore.menuRenderList))

const changeMenuMode = () => {
  menuMode.value = !menuMode.value
  loginStore.$patch((state) => {
    state.menuMode = menuMode.value
  });
}

const unifiedPageRouteNames = new Set([
  'originalDatasetManage',
  'taskDatabaseManage',
  'preprocess',
  'intanceDatabase',
  'trainTask',
  'resultQuery',
  'datasetManageUnified',
  'taskDatasetManageDev',
])

const showUnifiedPageShell = computed(
  () => unifiedPageRouteNames.has(String(route.name || ''))
)

const unifiedPageTitle = computed(
  () => String(route.meta?.title || titleStore.title || '')
)

const unifiedRouteName = computed(
  () => String(route.name || '').replace(/[^a-zA-Z0-9_-]/g, '-')
)

const unifiedRouteClass = computed(
  () => showUnifiedPageShell.value ? `page-content--route-${unifiedRouteName.value}` : ''
)

const unifiedRouteShellClass = computed(
  () => showUnifiedPageShell.value ? `unified-page-shell--route-${unifiedRouteName.value}` : ''
)

const unifiedRouteCardClass = computed(
  () => showUnifiedPageShell.value ? `unified-page-card--route-${unifiedRouteName.value}` : ''
)

const showPageTitleBar = computed(
  () => !showUnifiedPageShell.value
)



//监听路由变化，设置title
watch(()=>route,(newV)=>{
  const {title}=newV.meta
  titleStore.title= title
},{immediate:true,deep:true})

let init_color = loginStore.topicColor || 'hsla(209, 100%, 56%, 1)';
setPrimaryColor(init_color)










const colorVisible = ref(false);
const colorTopic = ref(init_color);
const predefineColors = ref([
  'hsla(209, 100%, 56%, 1)',
  '#101010',
  '#ff4500',
  '#ff8c00',
  '#ffd700',
  '#90ee90',
  '#00ced1',
  '#1e90ff',
  '#c71585',
  'rgba(255, 69, 0, 0.68)',
  'rgb(255, 120, 0)',
  'hsv(51, 100, 98)',
  'hsva(120, 40, 94, 0.5)',
  'hsl(181, 100%, 37%)',
  '#c7158577',
  'rgb(128,128,128)',
])
const onChangeColor = () => {
  let color = colorTopic.value;
  if (color) {
    setPrimaryColor(color)
    loginStore.$patch((state) => {
      state.topicColor = color
    });
  }
}
const changTopic = () => {
  colorVisible.value = true
}

const router = useRouter();
const pwdForm = reactive({});

// 重置表单ref
const formRef = ref(null);
const validatePass = (rule, value, callback) => {
  if (value === "") {
    callback(new Error("请输入密码"));
  } else if (
    !/^(?![0-9]+$)(?![^0-9]+$)(?![a-zA-Z]+$)(?![^a-zA-Z]+$)[a-zA-Z0-9\S]{8,30}$/.test(
      value
    )
  ) {
    callback(
      new Error("请输入强密码：[数字,字母，特殊字符]至少包括2类，且长度不小于8")
    );
  } else {
    if (pwdForm.pwd3 !== "") {
      if (!formRef.value) return;
      formRef.value.validateField("pwd3");
    }
    callback();
  }
};
const validatePass2 = (rule, value, callback) => {
  if (value === "") {
    callback(new Error("请再输入一次密码"));
  } else if (value !== pwdForm.pwd2) {
    callback(new Error("两次密码不一致！"));
  } else {
    callback();
  }
};
// 表单验证
const rules = {
  pwd1: [
    { required: true, message: "请输入旧密码", trigger: ["change", "blur"] },
  ],
  pwd2: [
    {
      required: true,
      validator: validatePass,
      trigger: ["change", "blur"],
    },
  ],
  pwd3: [
    {
      required: true,
      validator: validatePass2,
      trigger: ["change", "blur"],
    },
  ],
};
const pwdVisible = ref(false);
const msgTime = ref(new Date().getTime());


// const isSys = userStore.user.type == 1;

const isSys=computed(()=>userStore.isSys)

const sendMsg = () => {
  msgTime.value = new Date().getTime();
};

const msgCnt = ref(0); // 告警消息总数
const msgList = ref([]); //最近的告警消息
const logout = () => {
  AuthService.logout().then((res) => {
    if (res.code === 0) {
      loginStore.$patch((state) => {
        state.isLogin = false;
        state.uuidLogin = "";
        state.token = "";
      });
      router.replace({ path: "/" });
    }
  });
};
const showPwdModal = () => {
  pwdForm.pwd1 = "";
  pwdForm.pwd2 = "";
  pwdForm.pwd3 = "";
  pwdVisible.value = true;
  nextTick(() => {
    formRef.value.resetFields();
  });
};
const updatePwd = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      let pwd1 = pwdForm.pwd1?.trim();
      let pwd2 = pwdForm.pwd2?.trim();
      let pwd3 = pwdForm.pwd3?.trim();
      if (!pwd1 || !pwd2 || !pwd3) {
        ElMessage.error("密码不能为空");
        return;
      }
      if (
        !/^(?![0-9]+$)(?![^0-9]+$)(?![a-zA-Z]+$)(?![^a-zA-Z]+$)[a-zA-Z0-9\S]{8,30}$/.test(
          pwd2
        )
      ) {
        ElMessage.error(
          "请输入强密码：[数字,字母，特殊字符]至少包括2类，且长度不小于8"
        );
        return;
      }
      if (pwd2 !== pwd3) {
        ElMessage.error("两次输入密码不一致");
        return;
      }

      UserService.updatePwd({
        username: userStore.user.username,
        pmd1: md5(pwd1),
        pmd2: md5(pwd2),
      }).then((res) => {
        if (res.code === 0) {
          clearTimeout(timer)
          ElMessage.success('密码修改成功！系统将自动退出，请重新登录')
          pwdVisible.value = false;
          loginStore.$patch((state) => {
            state.isLogin = false;
            state.uuidLogin = "";
            state.token = "";
          });
          timer = setTimeout(async () => {
            router.replace({ path: '/' });
          }, 2000);
        } else {
          ElMessage.error(res.msg || "修改失败");
        }
      });
    } else {
      return;
    }
  });
};
/**返回 */
const goback = () => {
  router.back();
};
const uuidLogin = loginStore.uuidLogin;



onMounted(() => {
  if (!uuidLogin) {
    ElMessage.error("尚未登录");
    return;
  } else {
    // wsConnect();
  }
});
onUnmounted(() => {
  // wscloseflg = true;
  // if (ws) {
  //   ws.close();
  // }
});
onBeforeUnmount(() => {
  clearTimeout(timer)
})
</script>

<style scoped>
.userBox {
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 15px;
  color: white;
}

.avatar-name {
  color: white;
}

.top-logo {
  height: 30px;
}

.header-layout {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2000;
  /* background: #074080; */
  /* 0b3768 */
  background: var(--el-color-primary);
  /* background: url('/imgs/header.png'); */
  color: white;
  height: 50px;
}

.avatar-user {
  background: url("/imgs/user.png");
  margin-right: 10px;
}

.body-layout {
  padding-top: 50px;
  min-height: 100vh;
}

.page-content {
  min-height: calc(100vh - 90px);
  padding: 0 10px;
}

.right-container {
  /* background: #dedede; */
  background: #fff;
}

.main-header {
  height: 40px;
}

.main-footer {
  height: 20px;
}

.page-content--unified {
  background:
    radial-gradient(circle at 0 0, rgba(64, 158, 255, 0.10), transparent 32%),
    linear-gradient(180deg, #f7faff 0%, #eef3f9 100%);
  padding: 20px 24px 24px !important;
  overflow: auto;
}

.unified-page-shell {
  box-sizing: border-box;
  width: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.unified-page-title-row {
  display: flex;
  align-items: center;
  min-height: 38px;
}

.unified-page-title {
  margin: 0;
  color: #172033;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0.5px;
}

.unified-page-card {
  box-sizing: border-box;
  width: 100%;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 18px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
}

.unified-page-card :deep(.content),
.unified-page-card :deep(.content-div),
.unified-page-card :deep(.page-shell),
.unified-page-card :deep(.unified-task-panel) {
  height: 100%;
  min-height: 0;
  padding: 0 !important;
  background: transparent !important;
}

.unified-page-card :deep(.content > .el-card),
.unified-page-card :deep(.content-div > .el-card),
.unified-page-card :deep(.original-dataset-panel) {
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  background: transparent !important;
}

.unified-page-card :deep(.content > .el-card > .el-card__body),
.unified-page-card :deep(.content-div > .el-card > .el-card__body),
.unified-page-card :deep(.original-dataset-panel > .el-card__body) {
  padding: 0 !important;
}

.unified-page-card :deep(.content > .el-card:not(.app-list-panel) > .el-card__footer),
.unified-page-card :deep(.content-div > .el-card:not(.app-list-panel) > .el-card__footer),
.unified-page-card :deep(.original-dataset-panel:not(.app-list-panel) > .el-card__footer) {
  padding: 12px 0 0 !important;
  border-top: none !important;
  background: transparent !important;
}

.unified-page-card :deep(.app-list-panel > .el-card__body),
.unified-page-card :deep(.app-list-panel > .el-card__footer) {
  padding: 0 !important;
  border: 0 !important;
  background: transparent !important;
}

.page-content--route-datasetManageUnified .unified-page-card {
  padding: 0;
}

.foot-text {
  color: #aeaeae;
  font-size: 8px;
  /* font-weight: 400; */
}

:deep(.el-badge) {
  --el-badge-radius: 10px;
  --el-badge-font-size: 8px;
  --el-badge-padding: 4px;
  --el-badge-size: 12px;
}


.custom-menu {
  background-color: var(--el-color-primary);
  border: none;
  width: calc(100vw - 300px);
}

.el-menu--horizontal.el-menu {
  border-bottom: none;
  --el-menu-horizontal-height: 50px;
}

.el-menu--horizontal>.custom-menu-item {
  color: #fff;
  /* 默认文字颜色 */
  transition: background-color 0.3s, color 0.3s;
  /* 平滑的背景色和文字色变化 */
}

.el-menu--horizontal>.custom-menu-item:hover {
  background-color: transparent;
  color: #fff !important;
}

:deep(.el-menu--horizontal>.el-sub-menu .el-sub-menu__title) {
  color: whitesmoke;
}

:deep(.el-menu--horizontal>.el-sub-menu.is-active .el-sub-menu__title) {
  color: whitesmoke;
}

/* 选中时 */
.el-menu--horizontal>.custom-menu-item.el-menu-item.my-active {
  border-bottom: none;
  background-color: #fff;
  color: var(--el-color-primary) !important;
}
/** 水平手动跳转ia-active */
.el-menu--horizontal>.el-menu-item.is-active {
  color: #fff !important;
}


.custom-menu-item-v {
  margin-top: 10px;
  margin-bottom: 10px;
}

.el-divider--horizontal {
  margin: 0px;
}

.custom-menu-item-v.el-menu-item.my-active {
  background-color: var(--el-color-primary);
  color: #fff;
}

.custom-menu-item-v.el-menu-item {
  color: gray
}
</style>


