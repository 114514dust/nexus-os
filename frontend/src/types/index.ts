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

/** 求职进度：线性阶段（rejected 是旁路终态，不在推进链上） */
export type JobStage =
  | 'wish'
  | 'applied'
  | 'written'
  | 'interview1'
  | 'interview2'
  | 'hr'
  | 'offer'
  | 'accepted'
  | 'rejected'

/** 投递类型：秋招 / 春招 / 实习 / 社招 / 国企 / 其他 */
export type JobCategory = 'autumn' | 'spring' | 'intern' | 'social' | 'soe' | 'other'

export interface JobApplication {
  id: number
  company: string
  jobTitle: string
  category: JobCategory
  stage: JobStage
  url: string | null
  nextAction: string | null
  /** yyyy-MM-dd，只到天 */
  nextAt: string | null
  starred: boolean
  note: string | null
  createdAt: string
  updatedAt: string
}

/** 时间线中的一条进展 */
export interface JobEvent {
  id: number
  jobId: number
  stage: JobStage
  note: string | null
  occurredAt: string
}

export interface JobDetail {
  job: JobApplication
  events: JobEvent[]
}

export interface JobStats {
  total: number
  active: number
  interviewing: number
  offers: number
  rejected: number
  replied: number
  replyRate: number
  upcoming: number
}

export interface LlmSettings {
  apiKey: string
  baseUrl: string
  model: string
}

/** 消息推送配置（群机器人 webhook） */
export interface NotifySettings {
  enabled: boolean
  /** feishu | wecom */
  channel: string
  webhookUrl: string
  /** 飞书签名校验密钥，留空表示机器人未开启签名 */
  secret: string
  onFeed: boolean
  onDigest: boolean
  /** 求职面试提醒 */
  onInterview: boolean
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
