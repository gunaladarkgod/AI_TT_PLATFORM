<template>
    <el-image :src="imageSrc" :preview-src-list="[imageSrc]" alt="" preview-teleported>
        <template #error>
            <el-text v-show="imageSrc">加载失败</el-text>
        </template>
    </el-image>
</template>

<script setup>
import { onMounted, ref, watchEffect } from 'vue';
import { FileService } from '../api/api';
const props = defineProps({
    url: String,
    bm: Boolean
});
const imageSrc = ref();

const getImg = (url, bm) => {
    FileService.getFile(url, 'blob').then(res => {
        if (res instanceof Blob) {
            if (bm) {
                const img = new Image();
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                img.src = URL.createObjectURL(res);
                img.onload = () => {
                    canvas.width = img.width;
                    canvas.height = img.height;
                    ctx.drawImage(img, 0, 0);
                    // 应用模糊滤镜
                    ctx.filter = 'blur(50px)';
                    ctx.drawImage(img, 0, 0);
                    canvas.toBlob((newBlob) => {
                        imageSrc.value = URL.createObjectURL(newBlob);
                    }, res.type);
                };
            } else {
                imageSrc.value = URL.createObjectURL(res)
            }
        } else {
            imageSrc.value = null;
        }
    }).catch(() => {
    })
}
watchEffect(() => {
    let url = props.url
    let bm = props.bm
    getImg(url, bm)
})

onMounted(() => {
    getImg(props.url, props.bm);
})

</script>