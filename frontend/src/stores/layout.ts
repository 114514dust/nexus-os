import { defineStore } from 'pinia'
import { fetchLayout, saveLayout } from '@/api/layout'
import type { GridItem, LayoutItemDto, WidgetDefinition } from '@/types'

export const useLayoutStore = defineStore('layout', {
  state: () => ({
    items: [] as GridItem[],
    loading: false,
    saving: false
  }),
  actions: {
    async load(pageId: number) {
      this.loading = true
      try {
        const layout = await fetchLayout(pageId)
        this.items = layout.items.map((it, idx) => ({
          ...it,
          i: `${it.widgetType}-${idx}-${Date.now()}`
        }))
      } finally {
        this.loading = false
      }
    },
    async save(pageId: number) {
      this.saving = true
      try {
        const items: LayoutItemDto[] = this.items.map(({ i: _i, ...rest }) => rest)
        await saveLayout(pageId, { items })
      } finally {
        this.saving = false
      }
    },
    addItem(def: WidgetDefinition, config?: Record<string, any>) {
      const maxY = this.items.reduce((m, it) => Math.max(m, it.y + it.h), 0)
      const item: GridItem = {
        i: `${def.type}-${Date.now()}`,
        widgetType: def.type,
        x: 0,
        y: maxY,
        w: def.defaultW,
        h: def.defaultH
      }
      if (config) item.config = config
      this.items.push(item)
    },
    removeItem(index: number) {
      this.items.splice(index, 1)
    }
  }
})
