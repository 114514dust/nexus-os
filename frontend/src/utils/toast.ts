import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let seq = 0

function push(message: string, type: ToastType, duration: number) {
  const id = ++seq
  state.items.push({ id, type, message })
  window.setTimeout(() => {
    const idx = state.items.findIndex((t) => t.id === id)
    if (idx >= 0) state.items.splice(idx, 1)
  }, duration)
}

export function toast(message: string, type: ToastType = 'info', duration = 3000) {
  push(message, type, duration)
}

export const toastSuccess = (m: string) => push(m, 'success', 3000)
export const toastError = (m: string, duration = 4000) => push(m, 'error', duration)
export const toastInfo = (m: string) => push(m, 'info', 3000)

/** 读取 toast 状态（供 ToastHost 渲染） */
export function useToastState() {
  return state
}

/** 把后端错误对象转成友好文案；特判 Agent 未启动这类常见运维问题 */
export function friendlyError(err: unknown): string {
  const msg = err instanceof Error ? err.message : String(err)
  if (msg.includes('Agent 服务')) {
    return 'Agent 服务未启动，请先启动 Python Agent（端口 9001）'
  }
  return msg
}
