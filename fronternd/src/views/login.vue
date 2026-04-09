<template>
  <div class="login-bg">
    <div class="flex-center login-div">
      <el-space>
        <el-icon class="text-white font-size-24">
          <el-image :src="logoPath"></el-image>
        </el-icon>
        <el-text class="text-white font-size-24">AI训练平台</el-text></el-space>
    </div>
    <div class="flex-center">
      <div class="login-box">
        <div class="flex-center msg-div"><el-text>用户登录</el-text></div>
        <div class="login-form">
          <el-form label-position="top" label-width="100px" :model="form" style="max-width: 460px">
            <el-form-item label="用户名">
              <el-input v-model="form.username" clearable @keyup.enter="submit">
                <template #prefix>
                  <i class="iconfont icon-yonghu"></i></template>
              </el-input>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.pwd" clearable type="password" show-password @keyup.enter="submit"><template
                  #prefix>
                  <i class="iconfont icon-key-fill"></i>
                </template>
              </el-input>
            </el-form-item>
          </el-form>
          <el-button class="login-btn" type="primary" @click="submit">登录</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from "@vue/reactivity";
import { ElMessage } from "element-plus";
import md5 from "js-md5";
import { useRouter } from "vue-router";
import { AuthService } from "../api/api";
import { useLoginStore, useUserStore ,useMenuStore} from "@/stores/index";
import { uuid } from "vue-uuid";
import { isEN } from "../utils/regex";
import { logoPath } from '../api/axios'

const router = useRouter();
const form = reactive({ username: "", pwd: "" });
// 默认按钮颜色
document.documentElement.style.setProperty('--el-color-primary', '#409eff');
const submit = () => {
  let name = form.username;
  if (!name) {
    ElMessage.warning('用户名不能为空');
    return;
  }
  if (!isEN(name)) {
    ElMessage.warning("用户名只允许包括: 大小写字母,数字,_");
    return;
  }
  if (!form.pwd) {
    ElMessage.warning("密码不能为空");
    return;
  }
  AuthService.login({
    username: name,
    pmd: md5(form.pwd),
  })
    .then(async (res) => {
      if (res.code === 0) {
        const token = res.data;
        //解析token,获取身份
        let payload = token.split(".")[1];
        let json = JSON.parse(atob(payload));
        const uuidLogin = uuid.v1();
        useUserStore().$patch((state) => {
          state.user = {
            username: form.username,
            id: json.id,
            type: json.type,
          };
          state.isSys=json.type==1
        });
        useLoginStore().$patch((state) => {
          state.isLogin = true;
          state.uuidLogin = uuidLogin;
          state.token = token;
        });
        try {
          await useMenuStore().setMenuList()
          if(useMenuStore().menuList.length>0){
             //  注册路由
            await  useMenuStore().registerRoutes()
            // 跳转到第一个菜单
            router.replace({path:useMenuStore().getFirstMenu()})
          }else{
            ElMessage.warning("该用户暂无菜单权限，请联系系统管理员");
          }
           
        } catch (error) {
          console.log(error,'erro')
        }
         
        // router.replace({ path: "/layout/trainTask" });
      } else {
        useLoginStore().$patch((state) => {
          state.isLogin = false;
          state.uuidLogin = "";
          state.token = "";
        });
        ElMessage.warning(res.msg || "登录失败");
      }
    })
    .catch((err) => {
      useLoginStore().$patch((state) => {
        state.isLogin = false;
      });
    });
};
</script>

<style scoped>
.login-logo {
  height: 40px;
}

.foot-logo {
  height: 20px;
}

.login-div {
  padding-top: 15vh;
}

.login-bg {
  background: url("/imgs/bg.png");
  background-size: cover;
  height: 100vh;
}

.div-top {
  padding-top: 12vh;
}

.login-box {
  text-align: center;
  /*让div内部文字居中*/
  width: 350px;
  margin: auto;
  background: #f0f0f0;
  margin: 20px;
  padding: 0px;
  border-radius: 4px;
}

.login-form {
  background: #ffffff;
  padding: 20px;
  border-radius: 12px 12px 8px 8px;
}

.msg-div {
  padding: 10px 0px;
}

.span-1 {
  font-size: 20px;
  font-weight: 600;
}

.login-btn {
  width: 100%;
}
</style>