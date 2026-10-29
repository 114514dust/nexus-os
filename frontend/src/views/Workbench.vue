<template>
  <div class="workbench">
    <header class="topbar">
      <div class="brand">中枢 Nexus</div>
      <div class="actions">
        <button class="btn" @click="panelOpen = !panelOpen">添加组件</button>
        <button class="btn primary" :disabled="store.saving" @click="store.save()">
          {{ store.saving ? '保存中…' : '保存布局' }}
        </button>
      </div>
    </header>

    <transition name="fade">
      <div v-if="panelOpen" class="panel">
        <button v-for="def in defs" :key="def.type" class="panel-card" @click="addWidget(def)">
          <div class="panel-name">{{ def.name }}</div>
          <div class="panel-desc">{{ def.description }}</div>
        </button>
      </div>
    </transition>

    <main class="canvas">
      <GridLayout
        v-model:layout="store.items"
        :col-num="12"
        :row-height="60"
        :is-draggable="true"
        :is-resizable="true"
        :vertical-compact="true"
        :margin="[12, 12]"
        @layout-updated="scheduleSave"
      >
        <GridItem
          v-for="(item, idx) in store.items"
          :key="item.i"
          :i="item.i"
          :x="item.x"
          :y="item.y"
          :w="item.w"
          :h="item.h"
        >
          <WidgetRenderer :type="item.widgetType" />
          <button class="remove" title="移除组件" @click="removeWidget(idx)">×</button>
        </GridItem>
      </GridLayout>

      <div v-if="store.items.length === 0" class="hint">
        点击右上角「添加组件」，开始搭建你的工作台
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import WidgetRenderer from '@/widgets/WidgetRenderer.vue'
import { useLayoutStore } from '@/stores/layout'
import { fetchWidgets } from '@/api/widgets'
import type { WidgetDefinition } from '@/types'

const store = useLayoutStore()
const defs = ref<WidgetDefinition[]>([])
const panelOpen = ref(false)
let saveTimer: number | undefined

onMounted(async () => {
  store.load()
  try {
    defs.value = await fetchWidgets()
  } catch {
    /* 目录加载失败不阻塞工作台 */
  }
})

function addWidget(def: WidgetDefinition) {
  store.addItem(def)
  scheduleSave()
}

function removeWidget(idx: number) {
  store.removeItem(idx)
  scheduleSave()
}

function scheduleSave() {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => store.save(), 800)
}
</script>

<style scoped>
.hint {
  text-align: center;
  color: var(--text-2);
  padding: 40px;
}
</style>
