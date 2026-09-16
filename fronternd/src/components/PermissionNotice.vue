<template>
  <el-alert v-if="!canMaintain(store.user)" class="permission-notice" title="当前为只读数据与实验权限" description="可以查询、预览已有数据集，创建和运行训练任务、查看结果。数据集导入、修改、删除、预处理与结果维护由维护人员操作。" type="info" show-icon :closable="false" />
</template>
<script setup>
import { useUserStore } from '@/stores'
import { canMaintain } from '@/config/permissions'
const store = useUserStore()
</script>
<style scoped>.permission-notice { margin-bottom: 16px; }</style>
