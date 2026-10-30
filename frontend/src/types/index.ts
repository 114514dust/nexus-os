export interface WidgetDefinition {
  id: number
  type: string
  name: string
  description: string
  defaultW: number
  defaultH: number
  icon: string
}

// 后端布局项（不含 grid 的 i 字段）
export interface LayoutItemDto {
  widgetType: string
  x: number
  y: number
  w: number
  h: number
  config?: Record<string, any>
}

export interface LayoutDto {
  items: LayoutItemDto[]
}

// grid 布局项（含 i，供 grid-layout-plus 使用）
export interface GridItem extends LayoutItemDto {
  i: string | number
}

export interface Todo {
  id: number
  content: string
  done: boolean
  createdAt: string
}

export interface Note {
  id: number
  content: string
  createdAt: string
  updatedAt: string
}

export interface Link {
  id: number
  title: string
  url: string
  sortOrder: number
  createdAt: string
}

export interface Message {
  id: number
  source: string
  channel: string | null
  sender: string | null
  content: string
  receivedAt: string
  processed: boolean
}

export interface Summary {
  id: number
  summaryDate: string
  content: string
  messageCount: number
  todoCount: number
  noteCount: number
  llmMode: string | null
  createdAt: string
}

export interface Subscription {
  id: number
  type: string
  name: string
  url: string
  enabled: boolean
  lastFetchedAt: string | null
  createdAt: string
}

export interface AlgorithmQuestion {
  id: number
  title: string
  difficulty: string // easy | medium | hard
  leetcodeUrl: string
  frequency: number // 面试出现频率（热度）
  star: number // 用户自评星级 0~5
  note: string | null // 解题思路 / 代码笔记
  createdAt: string
  updatedAt: string
}

export interface LlmSettings {
  apiKey: string
  baseUrl: string
  model: string
}

export interface Page {
  id: number
  name: string
  sortOrder: number
  createdAt: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 后端持久化后返回的字段（前端本地发送时为 undefined） */
  id?: number
  intent?: string
  createdAt?: string
  /** 仅前端本地标记：该条为发送失败的提示，可重试 */
  error?: boolean
  /** 仅前端本地标记：该条正在流式接收中 */
  streaming?: boolean
}

export interface ChatReply {
  reply: string
  intent: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
