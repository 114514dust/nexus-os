import { defineStore } from 'pinia'

type Handler = () => void

/**
 * 领域 -> 订阅者集合。
 *
 * 放在模块级而不是 Pinia state 里：它是一张「回调表」，不参与渲染，
 * 放进响应式系统只会白白增加依赖追踪开销。
 */
const handlers = new Map<string, Set<Handler>>()

/** 订阅某个领域的变更，返回取消订阅函数 */
export function onDataChange(domain: string, fn: Handler): () => void {
  let set = handlers.get(domain)
  if (!set) {
    set = new Set()
    handlers.set(domain, set)
  }
  set.add(fn)
  return () => {
    set.delete(fn)
    if (set.size === 0) handlers.delete(domain)
  }
}

/** 是否已有订阅者（避免组件未挂载时的无效分发） */
export function hasSubscriber(domain: string): boolean {
  return (handlers.get(domain)?.size ?? 0) > 0
}

/** 单例连接：整个应用只保留一条 EventSource */
let source: EventSource | null = null

export const useEventsStore = defineStore('events', {
  state: () => ({
    connected: false,
    /** 最近一次收到的变更领域，便于调试 */
    lastDomain: ''
  }),
  actions: {
    /**
     * 建立变更订阅（幂等）。
     *
     * 用 EventSource 而不是 fetch+Stream：它是 GET 长连接、浏览器原生支持
     * 断线自动重连（后端重启后会自动恢复），这里不需要请求体或自定义 header。
     */
    connect() {
      if (source) return
      const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
      source = new EventSource(`${base}/events`)

      // 后端握手成功即认为通道可用
      source.addEventListener('hello', () => {
        this.connected = true
      })

      source.addEventListener('change', (ev) => {
        let domain = ''
        try {
          domain = JSON.parse((ev as MessageEvent).data)?.domain ?? ''
        } catch {
          return
        }
        if (!domain) return

        this.lastDomain = domain
        const set = handlers.get(domain)
        if (!set) return
        for (const fn of set) {
          try {
            fn()
          } catch (e) {
            console.error('[live] 刷新失败:', domain, e)
          }
        }
      })

      // 连接异常时只更新状态，重连交给浏览器（EventSource 内置，默认 3s 重试）
      source.onerror = () => {
        this.connected = false
      }
    },

    disconnect() {
      source?.close()
      source = null
      this.connected = false
    }
  }
})

// 开发期热更新时关闭旧连接，避免同一页面累积多条 EventSource
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    source?.close()
    source = null
  })
}
