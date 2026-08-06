import { onActivated, onMounted, onUnmounted } from 'vue'

/**
 * 数据集模块统一刷新中心。
 * 页面只订阅自己依赖的数据范围；任何写操作完成后广播范围即可，避免组件层层传 refreshKey。
 */
export const DatasetScope = Object.freeze({
  ORIGINAL: 'original',
  TASK: 'task',
  MID: 'mid',
  INSTANCE: 'instance'
})

const subscribers = new Set()

function normalizeScopes(scopes) {
  return new Set((Array.isArray(scopes) ? scopes : [scopes]).filter(Boolean))
}

export function notifyDatasetRefresh(scopes, reason = '') {
  const changed = normalizeScopes(scopes)
  if (!changed.size) return
  const event = { scopes: changed, reason, at: Date.now() }
  subscribers.forEach((subscriber) => {
    if ([...subscriber.scopes].some((scope) => changed.has(scope))) subscriber.handler(event)
  })
}

/**
 * 订阅统一刷新事件，并在组件初次挂载/再次激活时重新读取。
 * 同时到来的刷新会合并为下一轮，防止慢请求覆盖刚完成操作后的新数据。
 */
export function useDatasetRefresh(scopes, loader) {
  const subscribedScopes = normalizeScopes(scopes)
  let loading = false
  let pending = false
  let disposed = false

  const refresh = async () => {
    if (disposed) return
    if (loading) {
      pending = true
      return
    }
    loading = true
    try {
      await loader()
    } finally {
      loading = false
      if (pending && !disposed) {
        pending = false
        await refresh()
      }
    }
  }

  const subscriber = { scopes: subscribedScopes, handler: () => { void refresh() } }
  subscribers.add(subscriber)
  onMounted(() => { void refresh() })
  onActivated(() => { void refresh() })
  onUnmounted(() => {
    disposed = true
    subscribers.delete(subscriber)
  })
  return { refresh }
}
