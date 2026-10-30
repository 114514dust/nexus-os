import { onMounted, onUnmounted } from 'vue'
import { onDataChange } from '@/stores/events'

/**
 * 组件级「数据变了就刷新」的接入点。
 *
 * 用法（一行）：
 *   useLiveReload('todo', () => store.load())
 *
 * 说明：
 * - 订阅在 onMounted 注册、onUnmounted 注销，组件销毁不会留下悬空回调；
 * - 带 150ms 防抖——批量操作或一次性抓取多条消息时，
 *   会连续收到多个事件，合并成一次刷新，避免多次请求打满；
 * - reload 的错误被吞掉：刷新失败不应该冒泡成全局报错，
 *   用户下次操作或下次事件到来时会自然重试。
 */
export function useLiveReload(domain: string, reload: () => unknown, delay = 150) {
  let timer: ReturnType<typeof setTimeout> | null = null
  let off: (() => void) | null = null

  onMounted(() => {
    off = onDataChange(domain, () => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        timer = null
        Promise.resolve(reload()).catch((e) => {
          console.warn('[live] 自动刷新失败:', domain, e)
        })
      }, delay)
    })
  })

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
    off?.()
    off = null
  })
}
