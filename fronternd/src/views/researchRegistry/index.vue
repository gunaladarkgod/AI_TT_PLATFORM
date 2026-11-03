<template>
  <div class="registry-page">
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :description="errorDetails"
      show-icon
      :closable="false"
      class="registry-alert"
    />

    <template v-else-if="loading">
      <el-skeleton :rows="8" animated />
    </template>

    <template v-else>
      <div class="registry-intro">
        <div>
          <h2>{{ registry.registry?.name || '研究基线公约' }}</h2>
          <p>{{ registry.registry?.description }}</p>
        </div>
        <el-tag type="info">只读登记</el-tag>
      </div>

      <el-empty v-if="!registry.directions?.length && !registry.algorithms?.length" description="当前没有可展示的登记内容" />

      <section v-if="registry.directions?.length" class="registry-section">
        <div class="section-heading">
          <h3>研究方向</h3>
          <span>公共数据格式、评测指标和引擎兼容条件</span>
        </div>
        <div class="direction-grid">
          <el-card v-for="direction in registry.directions" :key="direction.id" shadow="never" class="direction-card">
            <template #header>
              <div class="card-heading">
                <strong>{{ direction.name }}</strong>
                <el-tag size="small" :type="direction.status === 'active' ? 'success' : 'info'">{{ direction.status }}</el-tag>
              </div>
            </template>
            <p>{{ direction.description }}</p>
            <div class="registry-meta"><b>数据格式</b><span>{{ (direction.data_formats || []).join('、') || '—' }}</span></div>
            <div class="registry-meta"><b>评测指标</b><span>{{ (direction.metrics || []).join('、') || '—' }}</span></div>
            <div class="registry-meta"><b>引擎兼容</b><span>{{ compatibilityText(direction.engine_compatibility) }}</span></div>
          </el-card>
        </div>
      </section>

      <section class="registry-section">
        <div class="section-heading">
          <h3>算法登记</h3>
          <span>登记信息用于识别兼容条件；源码访问仍由独立算法仓库控制</span>
        </div>
        <el-table :data="registry.algorithms || []" stripe border size="small" empty-text="当前没有已登记算法">
          <el-table-column prop="name" label="名称" min-width="230" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="engine.id" label="引擎" width="120"><template #default="{ row }">{{ row.engine?.id || '—' }}</template></el-table-column>
          <el-table-column label="版本" width="110"><template #default="{ row }">{{ row.engine?.version || '—' }}</template></el-table-column>
          <el-table-column prop="data_format" label="数据格式" width="110" />
          <el-table-column label="最低平台版本" width="140"><template #default="{ row }">{{ row.platform?.min_version || '未指定' }}</template></el-table-column>
          <el-table-column label="仓库" min-width="220" show-overflow-tooltip>
            <template #default="{ row }"><a :href="row.repository?.url" target="_blank" rel="noopener">{{ row.repository?.url || '未登记' }}</a></template>
          </el-table-column>
        </el-table>
      </section>

      <section class="registry-section">
        <div class="section-heading"><h3>公约文档</h3><span>登记字段和实验约束</span></div>
        <el-collapse>
          <el-collapse-item v-for="document in registry.conventions || []" :key="document.id" :name="document.id" :title="document.title">
            <pre class="convention-content">{{ document.content }}</pre>
          </el-collapse-item>
        </el-collapse>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ResearchRegistryService } from '@/api/api'

const loading = ref(true)
const registry = ref({ directions: [], algorithms: [], conventions: [], registry: {} })
const errorMessage = ref('')
const errorDetails = ref('')

const statusLabels = { draft: '草稿', review: '评审中', published: '已发布' }
const statusLabel = (status) => statusLabels[status] || status || '未知'
const statusType = (status) => ({ draft: 'info', review: 'warning', published: 'success' }[status] || 'danger')
const compatibilityText = (items = []) => (items || []).map(item => `${item.engine} ${item.versions?.join('/') || ''}`).join('、') || '—'

onMounted(async () => {
  try {
    const response = await ResearchRegistryService.get()
    if (response?.code !== 0) {
      registry.value = response?.data || registry.value
      errorMessage.value = response?.msg || '研究基线登记读取失败'
      errorDetails.value = (response?.data?.errors || []).join('；')
    } else {
      registry.value = response.data || registry.value
    }
  } catch (error) {
    registry.value = error?.data || registry.value
    errorMessage.value = error?.msg || '研究基线登记读取失败'
    errorDetails.value = (error?.data?.errors || []).join('；')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.registry-page { padding: 4px 2px 28px; }
.registry-alert { margin-bottom: 18px; }
.registry-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.registry-intro h2, .section-heading h3 { margin: 0; color: var(--el-text-color-primary); }
.registry-intro p, .section-heading span, .direction-card p { color: var(--el-text-color-secondary); line-height: 1.6; }
.registry-intro p { margin: 8px 0 0; }
.registry-section { margin-top: 24px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.direction-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; }
.direction-card { border-radius: 6px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.direction-card p { margin: 0 0 14px; }
.registry-meta { display: grid; grid-template-columns: 78px 1fr; gap: 8px; margin-top: 8px; font-size: 13px; }
.registry-meta b { color: var(--el-text-color-primary); }
.registry-meta span { color: var(--el-text-color-secondary); }
a { color: var(--el-color-primary); }
.convention-content { margin: 0; padding: 12px; background: var(--el-fill-color-light); color: var(--el-text-color-regular); white-space: pre-wrap; font: inherit; line-height: 1.6; }
@media (max-width: 700px) { .section-heading { display: block; } .section-heading span { display: block; margin-top: 5px; } .registry-intro { display: block; } .registry-intro .el-tag { margin-top: 10px; } }
</style>
