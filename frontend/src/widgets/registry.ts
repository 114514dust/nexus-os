import { defineAsyncComponent, type Component } from 'vue'

export interface WidgetMeta {
  type: string
  name: string
  description: string
  icon: string
  defaultW: number
  defaultH: number
  component: Component
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
    component: defineAsyncComponent(() => import('./LinksWidget.vue'))
  })
}
