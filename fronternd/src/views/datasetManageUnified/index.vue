<template>
  <div class="page-shell">
    <div class="doc-layout">
      <aside class="doc-left">
        <div class="doc-left-placeholder"></div>
      </aside>

      <main class="doc-main">
        <section id="page-top" class="doc-section page-hero">
          <div class="page-title">数据集管理（dev）</div>
          <div class="page-subtitle">
            在同一页面串联原始数据集、任务管理、实例预处理与实例数据集，风格与「任务数据集管理（dev）」一致。
          </div>
        </section>

        <section id="sec-original" class="doc-section section-block section-block--fixed-height">
          <div class="section-heading">
            <div class="section-title-toolbar-row">
              <h2 class="section-title">
                原始数据集
                <a class="section-link" href="#sec-original" @click.prevent="scrollToAnchor('#sec-original')">#</a>
              </h2>
            </div>
            <div class="section-desc-row">
              <p class="section-desc section-desc--inline">
                与独立「原始数据集」页相同的数据；表头支持排序与列筛选。CVAT 刷新、导入与主页入口在内容区右上角「数据操作」菜单。列表/卡片切换在此行右侧。
              </p>
              <div class="section-task-toolbar" @click.stop>
                <el-switch
                  v-model="originalViewAsTable"
                  inline-prompt
                  active-text="列表"
                  inactive-text="卡片"
                />
              </div>
            </div>
          </div>
          <div class="section-body section-embed section-embed--original">
            <OriginalDatasetManage
              v-model:view-as-table="originalViewAsTable"
              :embed-mode="true"
            />
          </div>
        </section>

        <section id="sec-task" class="doc-section section-block section-block--fixed-height">
          <div class="section-heading">
            <div class="section-title-toolbar-row">
              <h2 class="section-title">
                任务管理
                <a class="section-link" href="#sec-task" @click.prevent="scrollToAnchor('#sec-task')">#</a>
              </h2>
            </div>
            <div class="section-desc-row">
              <p class="section-desc section-desc--inline">
                查看已有任务，并执行编辑或删除操作。列表/卡片在右侧切换；排序与列筛选在表头，下方为清除筛选/排序与搜索，与「原始数据集」同位置。
              </p>
              <div class="section-task-toolbar" @click.stop>
                <el-switch
                  v-model="taskViewAsTable"
                  inline-prompt
                  active-text="列表"
                  inactive-text="卡片"
                />
              </div>
            </div>
          </div>
          <div class="section-body section-embed section-embed--task">
            <TaskManagementUnifiedPanel
              v-model:task-view-as-table="taskViewAsTable"
            />
          </div>
        </section>

        <section id="sec-preprocess" class="doc-section section-block section-block--fixed-height">
          <div class="section-heading">
            <h2 class="section-title">
              实例数据集预处理
              <a class="section-link" href="#sec-preprocess" @click.prevent="scrollToAnchor('#sec-preprocess')">#</a>
            </h2>
            <div class="section-desc-row section-desc-row--single">
              <p class="section-desc section-desc--inline">
                选择中间实例数据集、增强/增广脚本并创建实例数据集（与独立预处理页一致）。
              </p>
            </div>
          </div>
          <div class="section-body section-embed section-embed--preprocess">
            <PreprocessPage :embed-mode="true" :embed-hide-create-title="true" />
          </div>
        </section>

        <section id="sec-instance" class="doc-section section-block section-block--fixed-height">
          <div class="section-heading">
            <h2 class="section-title">
              实例数据集
              <a class="section-link" href="#sec-instance" @click.prevent="scrollToAnchor('#sec-instance')">#</a>
            </h2>
            <div class="section-desc-row">
              <p class="section-desc section-desc--inline">
                按任务查看中间实例数据集与训测划分等；表头支持排序与列筛选，下方为清除与搜索（与「原始数据集」一致）。
              </p>
              <div class="section-task-toolbar" @click.stop>
                <el-switch
                  v-model="instanceViewAsTable"
                  inline-prompt
                  active-text="列表"
                  inactive-text="卡片"
                />
              </div>
            </div>
          </div>
          <div class="section-body section-embed section-embed--instance">
            <InstanceDatabasePage
              v-model:task-view-as-table="instanceViewAsTable"
              :embed-mode="true"
              embed-unified-toolbar
            />
          </div>
        </section>
      </main>

      <aside class="doc-right">
        <div class="anchor-wrapper">
          <div class="anchor-title">Contents</div>
          <el-anchor :offset="90" class="page-anchor" @click="handleAnchorClick">
            <el-anchor-link href="#sec-original" title="原始数据集" />
            <el-anchor-link href="#sec-task" title="任务管理" />
            <el-anchor-link href="#sec-preprocess" title="实例数据集预处理" />
            <el-anchor-link href="#sec-instance" title="实例数据集" />
          </el-anchor>
        </div>
      </aside>
    </div>
    <el-backtop :right="36" :bottom="40" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import OriginalDatasetManage from '@/views/originalDatasetManage/index.vue'
import TaskManagementUnifiedPanel from '@/views/datasetManageUnified/TaskManagementUnifiedPanel.vue'
import PreprocessPage from '@/views/preprocess/index.vue'
import InstanceDatabasePage from '@/views/intanceDatabase/index.vue'

const originalViewAsTable = ref(true)
const taskViewAsTable = ref(true)
const instanceViewAsTable = ref(true)

function scrollToAnchor(href) {
  if (!href || typeof href !== 'string') return
  const target = document.querySelector(href)
  if (!target) return
  const top = target.getBoundingClientRect().top + window.scrollY - 84
  window.scrollTo({
    top: Math.max(top, 0),
    behavior: 'smooth'
  })
}

function handleAnchorClick(e, href) {
  e?.preventDefault?.()
  scrollToAnchor(href)
}

const route = useRoute()

function applyScrollFromQuery() {
  const raw = route.query.scroll ?? route.query.section
  if (raw == null || raw === '') return
  const s = typeof raw === 'string' ? raw : String(raw)
  const href = s.startsWith('#') ? s : `#${s}`
  nextTick(() => {
    setTimeout(() => scrollToAnchor(href), 80)
  })
}

onMounted(() => applyScrollFromQuery())
watch(
  () => route.query,
  () => applyScrollFromQuery(),
  { deep: true }
)
</script>

<style scoped lang="scss">
.doc-layout {
  --dm-unified-section-height: min(70vh, 700px);
  display: flex;
  gap: 24px;
  align-items: flex-start;
  background: #ffffff;
}

.doc-left {
  width: 220px;
  min-width: 220px;
}

.doc-left-placeholder {
  min-height: calc(100vh - 120px);
}

.doc-main {
  flex: 1;
  min-width: 0;
  max-width: 1240px;
}

.doc-right {
  width: 220px;
  min-width: 220px;
}

.anchor-wrapper {
  position: fixed;
  top: 88px;
  right: 10px;
  width: 200px;
  padding-top: 8px;
  background: transparent;
  z-index: 10;
}

.anchor-title {
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #606266;
}

.doc-section {
  margin-bottom: 28px;
}

.page-hero {
  padding: 60px 0 4px;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: #222;
}

.page-subtitle {
  margin-top: 8px;
  color: #666;
  font-size: 14px;
  line-height: 1.7;
}

.page-shell {
  padding: 0;
  background: #ffffff;
  color: #303133;
}

.section-block {
  background: #fff;
  border: 1px solid #e6e8ee;
  border-radius: 12px;
  overflow: visible;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

/*
 * 统合页主区块：大标题（section-heading）与内部页脚分页不参与固定高度；
 * --dm-unified-section-height 仅约束 .section-body（中间嵌入区）。
 */
.section-block.section-block--fixed-height {
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.section-block.section-block--fixed-height > .section-heading {
  flex-shrink: 0;
}

.section-block.section-block--fixed-height > .section-body {
  height: var(--dm-unified-section-height, 600px);
  max-height: var(--dm-unified-section-height, 600px);
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  -webkit-overflow-scrolling: touch;
}

/* 任务管理：与原始数据集一致，大标题在 section-heading，内层 el-card 主体+footer 分页在 .content 内 */
#sec-task.section-block--fixed-height > .section-body.section-embed--task {
  height: var(--dm-unified-section-height, 600px);
  max-height: var(--dm-unified-section-height, 600px);
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

#sec-task.section-block--fixed-height :deep(.unified-task-panel) {
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

#sec-task.section-block--fixed-height :deep(.unified-task-panel .content) {
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-heading {
  padding: 22px 24px 14px;
  border-bottom: 1px solid #ededed;
  background: #ffffff;
}

.section-title-toolbar-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 16px;
  flex-wrap: wrap;
}

.section-title-toolbar-row .section-title {
  margin: 0;
  flex: 1;
  min-width: 0;
}

.section-desc--below-title {
  margin: 10px 0 0;
}

.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #222;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-link {
  color: #409eff;
  font-size: 16px;
  font-weight: 500;
  text-decoration: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.section-title:hover .section-link {
  opacity: 1;
}

.section-desc {
  margin: 8px 0 0;
  color: #777;
  font-size: 13px;
  line-height: 1.6;
}

/* 与任务管理区块一致：说明与右侧工具栏同一行 */
.section-desc-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px 16px;
  flex-wrap: wrap;
}

.section-desc-row--single {
  justify-content: flex-start;
}

.section-desc--inline {
  margin: 0;
  flex: 1;
  min-width: 0;
}

.section-task-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.section-body {
  padding: 20px 24px 24px;
  min-width: 0;
  max-width: 100%;
}

.section-embed {
  padding: 0;
  margin: 0;
  min-width: 0;
  max-width: 100%;
}

/* 与「原始数据集」一致：内层 el-card 与「大标题」白底卡片左右留白 */
.section-body.section-embed.section-embed--task,
.section-body.section-embed.section-embed--instance {
  padding: 8px 10px 12px;
}

.section-body.section-embed.section-embed--original {
  padding: 8px 10px 12px;
}

.section-embed--original :deep(.content) {
  padding: 0;
  background: transparent;
  min-width: 0;
  max-width: 100%;
  flex: 1 1 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.section-embed--original :deep(.table-div--embed-scroll) {
  overflow-x: auto;
  max-width: 100%;
  padding-bottom: 16px;
}

.section-embed--task :deep(.table-div--embed-scroll),
.section-embed--instance :deep(.table-div--embed-scroll) {
  padding-bottom: 16px;
}

.section-embed--preprocess :deep(.content-div) {
  min-width: 0;
  max-width: 100%;
  flex: 1 1 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.section-embed--instance :deep(.content) {
  min-width: 0;
  max-width: 100%;
  flex: 1 1 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-embed--instance :deep(.table-div) {
  overflow-x: auto;
  max-width: 100%;
}

.section-embed--instance :deep(.split-layout) {
  overflow-x: hidden;
}

.page-anchor :deep(.el-anchor__marker) {
  background-color: #409eff;
}

.page-anchor :deep(.el-anchor__link) {
  color: #606266;
  background: transparent;
  font-size: 13px;
  line-height: 1.75;
}

.page-anchor :deep(.is-active > .el-anchor__link),
.page-anchor :deep(.el-anchor__link:hover) {
  color: #409eff;
  font-size: 13px;
  line-height: 1.75;
}
</style>
