<template>
  <div class="fileView">
    <div class="flex-between">
      <el-tag type="info">{{ curView.base }}</el-tag>
      <el-space wrap>
        <el-upload :show-file-list="false" :action="basePath_FILE_UPLOAD" :headers="{ 'Authorization': token }"
          :data="{ 'type': baseType, 'base': curView.base }" multiple :limit="50" :on-success="uploadSuccess"
          :on-error="uploadError">
          <el-icon class="iconfont icon-shangchuan" title="文件上传" v-if="canEdit"></el-icon>
        </el-upload>
        <el-button size="small" title="下载当前目录下的文件,不包括文件夹" @click="downZip" :loading="zipDownloading"
          v-if="baseType == 'train'">批量下载</el-button>
        <el-tag><el-text size="small">文件数:&nbsp;</el-text><el-text size="small"> {{ curView.f_files?.length }}</el-text>
          <el-text size="small" v-show="curView.files?.length == 500" title="最多显示500条记录">&nbsp;+</el-text>
        </el-tag>
        <el-input v-model="searchFile" placeholder="输入名称检索" size="small" @change="fileFilter">
        </el-input>
        <el-button @click="fileFilter" size="small"><el-icon><i class="iconfont icon-sousuo"></i></el-icon></el-button>
      </el-space>
    </div>
    <el-divider></el-divider>
    <el-container>
      <el-aside class="aside-container not-select">
        <el-tree :default-expand-all="false" ref="treeRef" node-key="base" :props="treeProps" :load="loadNode"
          @node-click="nodeClick" highlight-current lazy>
          <template #default="{ node }">
            <div class="my-tree-node flex-between">
              <el-text size="small">{{ node.label }}</el-text>
              <div class="my-hover">
                <el-space v-if="canEdit">
                  <el-button link @click.stop="addDir(node)" class="iconfont icon-a-Property1tianjiamulu fontSpan "
                    size="small" type="info" title="添加子目录"></el-button>
                  <el-button link @click.stop="deleteDir(node)" class="iconfont  icon-shanchu fontSpan " size="small"
                    type="info" title="删除目录" v-show="node.data.url"></el-button>
                </el-space>
                <el-space v-else-if="isTrain">
                  <!-- 特殊情况下开放 -->
                  <el-button link @click.stop="clearPt(node)" class="iconfont icon-qingkong fontSpan "
                    v-show="node?.data?.name == 'run'" size="small" type="info" title="清除所有中间权重文件"></el-button>
                  <el-button link @click.stop="clearPt(node)" class="iconfont  icon-qingkong fontSpan "
                    v-show="node.parent?.data?.name == 'run'" size="small" type="info" title="清除中间权重文件"></el-button>
                  <el-button link @click.stop="deleteDir(node)" class="iconfont  icon-shanchu fontSpan "
                    v-show="node.parent?.data?.name == 'run' || node.parent?.data?.name == 'val' || node.parent?.data?.name == 'predict'"
                    size="small" type="info" title="删除目录"></el-button>
                </el-space>
                <el-space v-else-if="baseType == 'task' && node.data.url.startsWith('/data_trans')">
                  <el-button link @click.stop="deleteDir(node)" class="iconfont  icon-shanchu fontSpan " size="small"
                    type="info" title="删除目录" v-show="node.data.url"></el-button>
                </el-space>
              </div>
            </div>
          </template>
        </el-tree>
      </el-aside>
      <el-main class="main-container">
        <el-space wrap size="large">
          <el-link v-for="(item, id) in curView.f_files" :key="id" :underline="false" @click="openFile(id)">
            <el-tag size="small" type="info">{{ item }}</el-tag>
          </el-link>
        </el-space>
      </el-main>
    </el-container>
  </div>
  <el-dialog v-model="fileVisible" width="60%" top="5vh" :lock-scroll="false" :title="curName" draggable
    :close-on-click-modal="true" :destroy-on-close="true">
    <div class="flex-end">
      <el-space>
        <el-button class="iconfont icon-shanchu" @click="delFile(curIdx)"></el-button>
        <el-button @click="goPre">上一个</el-button>
        <el-button @click="goNext">下一个</el-button>
      </el-space>
    </div>
    <div class="flex-center" v-if="curType == 'img'">
      <authimg :url="curFileUrl" :bm="curBm" style="width: 50%"></authimg>
    </div>
    <div v-else-if="curType == 'txt'">
      <el-button @click="downFile(curFileUrl)" :loading="dowdLoading" size="small">下载</el-button>
      <pre style="width: 100%;overflow: scroll" class="preBox">{{ cur_text }}</pre>
    </div>
    <div v-else>
      <el-button @click="downFile(curFileUrl, trainTaskRow)" :loading="dowdLoading" size="small">下载</el-button>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="fileVisible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref } from 'vue';
import { FileService, TrainTaskService } from '../api/api'
import { ElMessage, ElMessageBox } from 'element-plus';
import authimg from '../components/authimg.vue'
import { useLoginStore, useUserStore } from '../stores';
import { basePath_FILE_UPLOAD } from '../api/axios';

const props = defineProps({
  base_type: String,    // 目录类型
  base_path: String | Number,    // 相对根目录的位置
  base_uri: String,    // 浏览器访问根地址
  trainTaskRow: {  //模型训练 数据 
    type: Object,
    default: () => ({})
  }
});
const expName = ref("")
const token = useLoginStore()?.token;
const treeRef = ref(null)
const treeProps = { label: "name", isLeaf: "leaf" };
const curView = ref({ base: props.base_path + "" });
const searchFile = ref("");
const baseType = props.base_type;
const canEdit = baseType == 'calibrate' || baseType == 'model_trans' || baseType == 'train';
const isTrain = baseType == 'train';/** 例外的情况 */
/**加载树 */
const loadNode = (node, resolve) => {
  const { level } = node;
  if (level === 0) {
    let item = {
      leaf: false,
      name: "/",
      base: props.base_path,
      url: '',
    };
    let arr = [];
    arr.push(item);
    return resolve(arr);
  } else {
    let base = node.data?.base;
    let url = node.data?.url;
    FileService.queryFiles({ type: props.base_type, base: base }).then(res => {
      if (res.code === 0) {
        const nodes = res.data.dirs.map(item => {
          return {
            leaf: false,
            name: item,
            base: (base == '/' ? '' : base) + '/' + item,
            url: url + '/' + item,
          }
        })
        node.data.files = res.data.files;
        let curKey = treeRef.value?.getCurrentKey();
        if (curKey === base) {
          curView.value.files = node.data.files;
          fileFilter();
        }
        nodes.sort((a, b) => a.name.localeCompare(b.name))
        return resolve(nodes);
      } else {
        node.data.files = [];
        return resolve([]);
      }
    }).catch(() => {
      node.data.files = [];
      return resolve([]);
    })
  }

};
//过滤
const fileFilter = () => {
  let name = searchFile.value;
  if (!curView.value.files) {
    return;
  }
  curView.value.f_files = curView.value.files.filter((item) => {
    return item.lastIndexOf(name) >= 0;
  });
};
/**节点点击 */
const nodeClick = (t, node, tNode, e) => {
  curView.value.base = node.data.base; //绝对路径
  curView.value.url = node.data.url;  //url路径
  curView.value.files = node.data.files;
  if (node.parent?.data?.name == 'run' || node.data.name == "weights") {  //训练完成的模型 获取其 best.pt last.pt所属的exp名称 
    if (node.parent.data.name == "run") expName.value = node.data.name
    if (node.data.name == "weights") expName.value = node.parent.data.name
  } else {
    expName.value = null
  }
  fileFilter();
};

const isSys = useUserStore().user.type == 1;
const curFileUrl = ref('');
const curBm = ref(false);
const curType = ref('');
const curName = ref('');
const curIdx = ref(0);
const cur_text = ref('')
const fileVisible = ref(false);
/**打开文件 */
const img_suffs = ['.jpg', '.png', '.jpeg', '.bmp'];
const txt_suffs = ['.txt', '.yaml', '.xml', '.yml', '.csv', '.json', '.py'];
const regexbm = /^(val|train)_batch[0-9]+.*\.jpg$/;
const openFile = (idx) => {
  let name = curView.value.f_files[idx];
  let url = curView.value.url;
  curBm.value = !isSys && url.startsWith('/run/exp') && regexbm.test(name)
  curFileUrl.value = props.base_uri + '/' + url + '/' + name
  let suff = name.substring(name.lastIndexOf('.')).toLowerCase();
  curName.value = name;
  curIdx.value = idx;
  if (img_suffs.includes(suff)) {
    curType.value = 'img';
    fileVisible.value = true;
  } else if (txt_suffs.includes(suff)) {
    curType.value = 'txt'
    readTxt(curFileUrl.value);
    fileVisible.value = true;
  } else {
    curType.value = 'other'
    fileVisible.value = true;
  }

}
const goNext = () => {
  let idx = curIdx.value
  let list = curView.value.f_files || [];
  if (idx == list.length - 1) {
    ElMessage.success('已是最后一个');
  } else {
    openFile(idx + 1);
  }
}
const goPre = () => {
  let idx = curIdx.value
  if (idx == 0) {
    ElMessage.success('已是第一个');
  } else {
    openFile(idx - 1);
  }
}

const readTxt = (url) => {
  if (!url) return;
  FileService.getFile(url)
    .then((res) => {
      // 将内容转换为字符串类型（假设res原本可能是其他合适的数据类型，比如ArrayBuffer等，按需转换）
      const text = res.toString();
      if (text.length > 102400) {
        cur_text.value = text.substring(0, 102400) + "\n" + "........";
      } else {
        cur_text.value = text;
      }
    })
    .catch((e) => {
      cur_text.value = '';
      console.log(e);
    });
};
const dowdLoading = ref(false);
const downFile = (url, row = {}) => {
  let fileName = url.substring(url.lastIndexOf('/') + 1)
  let isBastLast = fileName.includes('best.pt') || fileName.includes('last.pt')
  if (row.name && expName.value && isBastLast) { //训练结果 best.pt last.pt  设置下载名称（name -  exp  - best/last.pt）
    fileName = `${row.name.replace(/\.pt$/, '')}-${expName.value}-${url.substring(url.lastIndexOf('/') + 1)}`          //正则 替换掉最后的文件后缀.pt
  }
  if (!url) return;
  url = url.replace(/\\/g, '/');
  dowdLoading.value = true;
  FileService.getFile(url, 'blob').then(blob => {
    const url2 = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url2;
    anchor.download = fileName;
    anchor.click();
    // 释放内存
    URL.revokeObjectURL(url2);
  }).finally(() => {
    dowdLoading.value = false;
  })
}
const zipDownloading = ref(false);
const downZip = () => {
  zipDownloading.value = true;
  FileService.downZipFile({ base: curView.value.base, type: baseType })
    .then(blob => {
      let fileName = curView.value.base.replace(/[\\/]/g, '_') + ".zip";
      const url2 = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url2;
      anchor.download = fileName;
      anchor.click();
      URL.revokeObjectURL(url2);
    })
    .finally(() => {
      zipDownloading.value = false;
    });
}

/**创建子目录 */
const addDir = (node) => {
  let base = node.data.base
  ElMessageBox.prompt('输入子目录名称', '', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_-]{1,20}$/,
    inputErrorMessage: '只允许[数字,字母,-,_]',
  })
    .then(({ value }) => {
      if (/^[a-zA-Z0-9_-]{1,20}$/.test(value)) {
        FileService.addDir({ type: baseType, base: base, name: value }).then(res => {
          if (res.code === 0) {
            ElMessage.success('创建成功');
            let n_node = {
              leaf: false,
              name: value, //文件名
              base: (base == '/' ? '' : base) + '/' + value, // 相对跟路径
              url: node.data.url + "/" + value,
            };
            treeRef.value?.append(n_node, node);
          } else {
            ElMessage.warning(res.msg)
          }
        })
      }
    })
    .catch(() => {
    })
}
/**上传成功 */
const uploadSuccess = (res, file) => {
  if (res.code === 0) {
    let name = file.name;
    let arr = curView.value.files || [];
    if (!arr.includes(name)) {
      arr.push(name);
      fileFilter();
    }
    ElMessage.success('上传成功');
  } else {
    ElMessage.warning(res.msg || '上传失败')
  }
}
/**上传失败 */
const uploadError = (err) => {
  console.log(err)
  ElMessage.warning('上传失败')
}

/**删除目录 */
const deleteDir = (node) => {
  let base = node.data.base
  let params = { type: baseType, base: base }
  ElMessageBox.confirm(
    `确定要删除[${node.label}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      FileService.delDir(params).then(res => {
        if (res.code === 0) {
          treeRef.value?.remove(node);
          node = null
          if (curView.value.base.startsWith(base)) {
            curView.value.base = ''
            curView.value.f_files = []
          }
          node = null
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {
    })
}
/**删除文件 */
const delFile = (idx) => {
  let curInfo = curView.value;
  let name = curInfo.f_files[idx];
  ElMessageBox.confirm(
    `确定要删除[${name}]?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      FileService.delFile({
        type: baseType,
        base: curInfo.base,
        name: name
      }).then(res => {
        if (res.code === 0) {
          fileVisible.value = false
          curIdx.value = 0;
          ElMessage.success('删除成功')
          curInfo.f_files.splice(idx, 1);
          let idx2 = curInfo.files.indexOf(name);
          if (idx2 != -1) {
            curInfo.files.splice(idx2, 1)
          }
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {
    })
}

const clearPt = (node) => {
  let name = node?.data?.name;
  let isAll = name == 'run';
  let id = parseInt(node?.data?.base.replace(/\\/g, '/').split('/')[0]);
  ElMessageBox.confirm(
    isAll ? '确定要删除所有的中间迭代权重文件吗?' : `确定要删除[${name}]的中间迭代权重文件吗?`,
    '',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      TrainTaskService.clearEpoches({
        id: id,
        expName: isAll ? '' : name,
      }).then(res => {
        if (res.code === 0) {
          ElMessage.success(res.msg)
          refreshNode(node)
        } else {
          ElMessage.warning(res.msg)
        }
      })
    })
    .catch(() => {
    })
}
const refreshNode = (node) => {
  loadNode(node, (nodes) => {
    treeRef.value?.updateKeyChildren(node.data?.base, nodes)
  });
};

</script>
<style scoped>
.aside-container {
  background: rgb(252, 252, 252);
  /* height: 60vh; */
}

.main-container {
  /* background: rgb(236, 245, 255); */
  background: rgb(255 238 236);
}

.file-iframe {
  height: calc(70vh);
  width: 100%;
}

.my-tree-node {
  width: 100%;
  padding-right: 10px;
}

.my-tree-node>.my-hover {
  display: none;
}

.my-tree-node:hover>.my-hover {
  display: block;
}

.fileView {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.fileView>.el-container {
  flex-grow: 1;
  overflow-y: auto;
}
</style>