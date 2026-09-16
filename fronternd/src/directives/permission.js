import { watchEffect } from 'vue'
import { useUserStore } from '@/stores'
import { canMaintain } from '@/config/permissions'

// 用于维护操作入口；隐藏时不会改写业务原有的 disabled 状态。
export default {
  mounted(el) {
    const originalDisplay = el.style.display
    el.__stopPermission = watchEffect(() => {
      el.style.display = canMaintain(useUserStore().user) ? originalDisplay : 'none'
    })
  },
  unmounted(el) { el.__stopPermission?.() },
}
