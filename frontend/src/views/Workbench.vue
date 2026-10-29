<template>
  <div class="workbench">
    <aside class="sidebar">
      <div class="sidebar-brand">中枢 Nexus</div>
      <nav class="sidebar-pages">
        <div
          v-for="page in pagesStore.pages"
          :key="page.id"
          class="page-item"
          :class="{ active: page.id === pagesStore.currentId }"
          @click="switchPage(page.id)"
        >
          <input
            v-if="renamingId === page.id"
            v-model="renameValue"
            class="page-rename-input"
            @keyup.enter="confirmRename"
            @blur="cancelRename"
            @click.stop
          />
          <span v-else class="page-name">{{ page.name }}</span>
          <div class="page-tools">
            <button class="page-tool" title="重命名" @click.stop="startRename(page)">✎</button>
            <button class="page-tool" title="删除" @click.stop="removePage(page)">×</button>
          </div>
        </div>
      </nav>

      <button v-if="!creating" class="add-page" @click="startCreate">＋ 新建页面</button>
      <input
        v-else
        v-model="createValue"
        class="add-page-input"
        placeholder="页面名称，回车确认"
        @keyup.enter="confirmCreate"
        @blur="cancelCreate"
      />
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="brand">{{ currentPageName }}</div>
        <div class="actions">
          <button class="btn" @click="settingsOpen = true">设置</button>
          <button class="btn" :class="{ active: editMode }" @click="toggleEdit">
            {{ editMode ? '退出 DIY' : 'DIY 模式' }}
          </button>
          <template v-if="editMode">
            <button class="btn" @click="panelOpen = !panelOpen">添加组件</button>
          </template>
        </div>
      </header>

      <transition name="fade">
        <div v-if="editMode && panelOpen" class="panel">
          <button v-for="def in defs" :key="def.type" class="panel-card" @click="addWidget(def)">
            <div class="panel-name">{{ def.name }}</div>
            <div class="panel-desc">{{ def.description }}</div>
          </button>
        </div>
      </transition>

      <main class="canvas" :class="{ editing: editMode }">
        <GridLayout
          v-model:layout="store.items"
          :col-num="12"
          :row-height="60"
          :is-draggable="editMode"
          :is-resizable="editMode"
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
            <div v-if="editMode" class="widget-tools">
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
          点击右上角「DIY 模式」，开始搭建这个页面
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

    <SettingsModal v-if="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GridLayout, GridItem } from 'grid-layout-plus'
import WidgetRenderer from '@/widgets/WidgetRenderer.vue'
import WidgetConfigPanel from '@/widgets/WidgetConfigPanel.vue'
import SettingsModal from '@/widgets/SettingsModal.vue'
import { getWidget } from '@/widgets/registry'
import { useLayoutStore } from '@/stores/layout'
import { usePagesStore } from '@/stores/pages'
import { fetchWidgets } from '@/api/widgets'
import type { Page, WidgetDefinition } from '@/types'

const store = useLayoutStore()
const pagesStore = usePagesStore()
const defs = ref<WidgetDefinition[]>([])
const panelOpen = ref(false)
const configIdx = ref<number | null>(null)
const editMode = ref(false)
const settingsOpen = ref(false)
const creating = ref(false)
const createValue = ref('')
const renamingId = ref<number | null>(null)
const renameValue = ref('')
let saveTimer: number | undefined

const currentPageName = computed(
  () => pagesStore.pages.find((p) => p.id === pagesStore.currentId)?.name ?? '中枢 Nexus'
)

const configMeta = computed(() =>
  configIdx.value !== null ? getWidget(store.items[configIdx.value]?.widgetType) : undefined
)

const configValue = computed(() =>
  configIdx.value !== null ? store.items[configIdx.value]?.config : undefined
)

onMounted(async () => {
  await pagesStore.load()
  if (pagesStore.currentId !== null) {
    await store.load(pagesStore.currentId)
  }
  try {
    defs.value = await fetchWidgets()
  } catch {
    /* 目录加载失败不阻塞工作台 */
  }
})

function toggleEdit() {
  editMode.value = !editMode.value
  if (!editMode.value) {
    panelOpen.value = false
    closeConfig()
    persistLayout()
  }
}

async function switchPage(id: number) {
  if (id === pagesStore.currentId) return
  pagesStore.select(id)
  editMode.value = false
  panelOpen.value = false
  closeConfig()
  await store.load(id)
}

function startCreate() {
  creating.value = true
  createValue.value = ''
}

async function confirmCreate() {
  const name = createValue.value.trim()
  creating.value = false
  if (!name) return
  const page = await pagesStore.create(name)
  editMode.value = false
  closeConfig()
  await store.load(page.id)
}

function cancelCreate() {
  creating.value = false
}

function startRename(page: Page) {
  renamingId.value = page.id
  renameValue.value = page.name
}

async function confirmRename() {
  const id = renamingId.value
  const name = renameValue.value.trim()
  renamingId.value = null
  if (id === null || !name) return
  await pagesStore.rename(id, name)
}

function cancelRename() {
  renamingId.value = null
}

async function removePage(page: Page) {
  if (!window.confirm(`确定删除页面「${page.name}」及其组件布局吗？`)) return
  await pagesStore.remove(page.id)
  editMode.value = false
  closeConfig()
  if (pagesStore.currentId !== null) {
    await store.load(pagesStore.currentId)
  } else {
    store.items = []
  }
}

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
  saveTimer = window.setTimeout(persistLayout, 800)
}

function persistLayout() {
  if (pagesStore.currentId !== null) {
    store.save(pagesStore.currentId)
  }
}
</script>
