<template>
    <el-button type="primary" @click="showLogger" size="small"><el-text size="small" class="text-white">查看日志</el-text>
    </el-button>
    <el-dialog v-model="logVisible" width="90%" top="1vh" title="查看日志" draggable :close-on-click-modal="true"
        :destroy-on-close="true" @close="onLogClose">
        <el-space wrap>
            <el-form-item label="日志类型" size="small">
                <el-select v-model="logType" @change="getLog" style="width: 100px;">
                    <el-option value="main" label="系统日志" v-show="isSys"></el-option>
                    <el-option value="train" label="模型训练"></el-option>
                    <el-option value="trans" label="模型转换"></el-option>
                    <el-option value="val" label="模型验证"></el-option>
                    <el-option value="predict" label="模型预测"></el-option>
                    <el-option value="data" label="数据集转换"></el-option>
                </el-select>
            </el-form-item>
            <el-form-item label="日志条数" size="small">
                <el-input-number :min="10" :max="100" v-model="cur_lines" :step="10" size="small"
                    @change="getLog"></el-input-number>
            </el-form-item>
            <el-form-item label="刷新间隔" size="small">
                <el-space>
                    <el-select v-model="logCheckFre" @change="onFreChange" size="small" style="width: 120px;">
                        <el-option :value="0" label="停止"></el-option>
                        <el-option :value="5000" label="5秒"></el-option>
                        <el-option :value="10000" label="10秒"></el-option>
                        <el-option :value="20000" label="20秒"></el-option>
                    </el-select>
                    <el-button size="small" type="primary" @click="getLog">刷新</el-button>
                </el-space>
            </el-form-item>
        </el-space>
        <div class="log-entry">
            {{ cur_log }}
        </div>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="logVisible = false">关闭</el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { FileService } from '../api/api';
import { useUserStore } from '../stores';
const props = defineProps({
    type: String,    // 目录类型
});
const logVisible = ref(false);
const logCheckFre = ref(0);
const cur_lines = ref(20)
const cur_log = ref('')
const isSys = useUserStore().user.type == 1;

const logType = ref(props.type || 'main')
const showLogger = () => {
    getLog()
    logVisible.value = true;
}
const getLog = () => {
    FileService.getLog({ lines: cur_lines.value, type: logType.value }).then(res => {
        if (res.code === 0) {
            cur_log.value = res.data.join('\n');
        } else {
            ElMessage.warning(res.msg)
        }
    })
}

let log_inter = null;
//日志自动刷新
const onFreChange = () => {
    clearInterval(log_inter)
    let time = logCheckFre.value
    if (time) {
        log_inter = setInterval(getLog, time);
    }
}
const onLogClose = () => {
    logCheckFre.value = 0
    clearInterval(log_inter)
}
onMounted(() => {
    getLog();
})
onBeforeUnmount(() => {
    onLogClose();
})
</script>
<style lang="css" scoped>
.log-entry {
    white-space: pre-wrap;
    /* 保留换行符并自动换行 */
    word-wrap: break-word;
    /* 长单词会换行 */
    max-height: 70vh;
    /* 设置日志显示区域的最大高度 */
    overflow-y: auto;
    /* 超出部分显示滚动条 */
    padding: 10px;
}
</style>