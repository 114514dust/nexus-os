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
          <WidgetRenderer :type="item.widgetType" :config="item.config" />
          <div class="widget-tools">
            <button
              v-if="hasConfig(item.widgetType)"
              class="tool config"
              title="配置组件"
              @click="openConfig(idx)"
            >
              ⚙
            </button>
            <button class="tool remove" title="移除组件" @click="removeWidget(idx)">×</button>
          </div>
        </GridItem>
      </GridLayout>

      <div v-if="store.items.length === 0" class="hint">
        点击右上角「添加组件」，开始搭建你的工作台
      </div>
    </main>

    <transition name="fade">
      <div v-if="configIdx !== null" class="config-mask" @click.self="closeConfig">
        <WidgetConfigPanel
          :meta="configMeta"
          :config="configValue"
          @cancel="closeConfig"
          @save="saveConfig"
        />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import WidgetRenderer from '@/widgets/WidgetRenderer.vue'
import WidgetConfigPanel from '@/widgets/WidgetConfigPanel.vue'
import { getWidget } from '@/widgets/registry'
import { useLayoutStore } from '@/stores/layout'
import { fetchWidgets } from '@/api/widgets'
import type { WidgetDefinition } from '@/types'

const store = useLayoutStore()
const defs = ref<WidgetDefinition[]>([])
const panelOpen = ref(false)
const configIdx = ref<number | null>(null)
let saveTimer: number | undefined

const configMeta = computed(() =>
  configIdx.value !== null ? getWidget(store.items[configIdx.value]?.widgetType) : undefined
)

const configValue = computed(() =>
  configIdx.value !== null ? store.items[configIdx.value]?.config : undefined
)

onMounted(async () => {
  store.load()
  try {
    defs.value = await fetchWidgets()
  } catch {
    /* 目录加载失败不阻塞工作台 */
  }
})

function addWidget(def: WidgetDefinition) {
  const meta = getWidget(def.type)
  const config = meta?.configSchema
    ? Object.fromEntries(
        meta.configSchema.filter((f) => f.default !== undefined).map((f) => [f.key, f.default])
      )
    : undefined
  store.addItem(def, config)
  scheduleSave()
}

function removeWidget(idx: number) {
  store.removeItem(idx)
  scheduleSave()
}

function hasConfig(type: string) {
  return !!getWidget(type)?.configSchema?.length
}

function openConfig(idx: number) {
  configIdx.value = idx
}

function closeConfig() {
  configIdx.value = null
}

function saveConfig(newConfig: Record<string, any>) {
  if (configIdx.value !== null) {
    store.items[configIdx.value].config = newConfig
  }
  closeConfig()
  scheduleSave()
}

function scheduleSave() {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => store.save(), 800)
}
</script>
