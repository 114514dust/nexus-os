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
    async load() {
      this.loading = true
      try {
        const layout = await fetchLayout()
        this.items = layout.items.map((it, idx) => ({
          ...it,
          i: `${it.widgetType}-${idx}-${Date.now()}`
        }))
      } finally {
        this.loading = false
      }
    },
    async save() {
      this.saving = true
      try {
        const items: LayoutItemDto[] = this.items.map(({ i: _i, ...rest }) => rest)
        await saveLayout({ items })
      } finally {
        this.saving = false
      }
    },
    addItem(def: WidgetDefinition) {
      const maxY = this.items.reduce((m, it) => Math.max(m, it.y + it.h), 0)
      this.items.push({
        i: `${def.type}-${Date.now()}`,
        widgetType: def.type,
        x: 0,
        y: maxY,
        w: def.defaultW,
        h: def.defaultH
      })
    },
    removeItem(index: number) {
      this.items.splice(index, 1)
    }
  }
})
