import { defineAsyncComponent, type Component } from 'vue'

export interface ConfigField {
  key: string
  label: string
  type: 'text' | 'number' | 'boolean' | 'select'
  default?: any
  options?: { value: string; label: string }[]
}

export interface WidgetMeta {
  type: string
  name: string
  description: string
  icon: string
  defaultW: number
  defaultH: number
  component: Component
  /** 组件可配置项（声明后，工作台会出现"配置"按钮） */
  configSchema?: ConfigField[]
}

// 组件注册表：Widget SDK 的核心，组件可插拔、可扩展
const registry = new Map<string, WidgetMeta>()

export function registerWidget(meta: WidgetMeta) {
  registry.set(meta.type, meta)
}

export function getWidget(type: string): WidgetMeta | undefined {
  return registry.get(type)
}

export function registerBuiltinWidgets() {
  registerWidget({
    type: 'clock',
    name: '时钟',
    description: '显示当前日期与时间',
    icon: 'clock',
    defaultW: 4,
    defaultH: 2,
    configSchema: [
      { key: 'showSeconds', label: '显示秒', type: 'boolean', default: true },
      { key: 'hour12', label: '12 小时制', type: 'boolean', default: false },
      { key: 'showDate', label: '显示日期', type: 'boolean', default: true }
    ],
    component: defineAsyncComponent(() => import('./ClockWidget.vue'))
  })
  registerWidget({
    type: 'todo',
    name: '待办清单',
    description: '记录并勾选待办事项',
    icon: 'todo',
    defaultW: 4,
    defaultH: 6,
    configSchema: [
      { key: 'title', label: '标题', type: 'text', default: '待办清单' },
      { key: 'showDone', label: '显示已完成', type: 'boolean', default: true }
    ],
    component: defineAsyncComponent(() => import('./TodoWidget.vue'))
  })
  registerWidget({
    type: 'note',
    name: '便签',
    description: '随手记的便签',
    icon: 'note',
    defaultW: 4,
    defaultH: 4,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '便签' }],
    component: defineAsyncComponent(() => import('./NoteWidget.vue'))
  })
  registerWidget({
    type: 'links',
    name: '快捷链接',
    description: '常用网址快捷入口',
    icon: 'links',
    defaultW: 4,
    defaultH: 3,
    configSchema: [
      { key: 'title', label: '标题', type: 'text', default: '快捷链接' },
      { key: 'openInNewTab', label: '新窗口打开（关闭则在组件内显示）', type: 'boolean', default: true }
    ],
    component: defineAsyncComponent(() => import('./LinksWidget.vue'))
  })
  registerWidget({
    type: 'message',
    name: '消息流',
    description: '汇聚各来源的消息',
    icon: 'message',
    defaultW: 6,
    defaultH: 8,
    configSchema: [
      { key: 'title', label: '标题', type: 'text', default: '消息流' },
      { key: 'refreshInterval', label: '刷新间隔(秒)', type: 'number', default: 15 },
      { key: 'pageSize', label: '显示条数', type: 'number', default: 50 }
    ],
    component: defineAsyncComponent(() => import('./MessageWidget.vue'))
  })
  registerWidget({
    type: 'summary',
    name: '每日总结',
    description: 'AI 生成的每日工作总结',
    icon: 'summary',
    defaultW: 6,
    defaultH: 8,
    configSchema: [
      { key: 'title', label: '标题', type: 'text', default: '每日总结' },
      { key: 'autoRefresh', label: '自动刷新', type: 'boolean', default: false }
    ],
    component: defineAsyncComponent(() => import('./SummaryWidget.vue'))
  })
  registerWidget({
    type: 'subscription',
    name: '订阅管理',
    description: '管理 RSS/B站订阅源并定时抓取',
    icon: 'subscription',
    defaultW: 6,
    defaultH: 8,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '订阅管理' }],
    component: defineAsyncComponent(() => import('./SubscriptionWidget.vue'))
  })
  registerWidget({
    type: 'algorithm',
    name: '高频算法题',
    description: '对标 CodeTop 的面试高频题，可标星级、记笔记、跳转 LeetCode',
    icon: 'algorithm',
    defaultW: 6,
    defaultH: 10,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '高频算法题' }],
    component: defineAsyncComponent(() => import('./AlgorithmWidget.vue'))
  })
  registerWidget({
    type: 'chat',
    name: 'AI 对话',
    description: '基于 Workflow 编排的 AI 助手，可直接操控待办/便签/链接/算法题等组件',
    icon: 'chat',
    defaultW: 6,
    defaultH: 10,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: 'AI 对话' }],
    component: defineAsyncComponent(() => import('./ChatWidget.vue'))
  })
  registerWidget({
    type: 'job',
    name: '求职追踪',
    description: '记录秋招/实习/社招投递，跟踪面试进度、提醒下一步，支持复盘笔记',
    icon: 'job',
    defaultW: 6,
    defaultH: 10,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '求职追踪' }],
    component: defineAsyncComponent(() => import('./JobWidget.vue'))
  })
  registerWidget({
    type: 'posting',
    name: '校招岗位',
    description: '聚合秋招/春招在招岗位，支持关键词匹配、一键加入求职追踪',
    icon: 'posting',
    defaultW: 6,
    defaultH: 10,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '校招岗位' }],
    component: defineAsyncComponent(() => import('./JobBoardWidget.vue'))
  })
  registerWidget({
    type: 'mail',
    name: '邮箱消息',
    description: '订阅 QQ 邮箱收信，自动挑出面试/笔试等重要邮件并转成待办',
    icon: 'mail',
    defaultW: 6,
    defaultH: 10,
    configSchema: [{ key: 'title', label: '标题', type: 'text', default: '邮箱消息' }],
    component: defineAsyncComponent(() => import('./MailWidget.vue'))
  })
}
