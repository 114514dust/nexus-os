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
    component: defineAsyncComponent(() => import('./ClockWidget.vue'))
  })
  registerWidget({
    type: 'todo',
    name: '待办清单',
    description: '记录并勾选待办事项',
    icon: 'todo',
    defaultW: 4,
    defaultH: 6,
    component: defineAsyncComponent(() => import('./TodoWidget.vue'))
  })
  registerWidget({
    type: 'note',
    name: '便签',
    description: '随手记的便签',
    icon: 'note',
    defaultW: 4,
    defaultH: 4,
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
      { key: 'openInNewTab', label: '新标签页打开', type: 'boolean', default: true }
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
    component: defineAsyncComponent(() => import('./MessageWidget.vue'))
  })
  registerWidget({
    type: 'summary',
    name: '每日总结',
    description: 'AI 生成的每日工作总结',
    icon: 'summary',
    defaultW: 6,
    defaultH: 8,
    component: defineAsyncComponent(() => import('./SummaryWidget.vue'))
  })
}
