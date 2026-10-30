<template>
  <div class="workbench">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
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
      <div class="sidebar-footer">
        <button class="sidebar-settings" @click="settingsOpen = true">⚙ 设置</button>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            class="sidebar-toggle"
            :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="toggleSidebar"
          >
            ☰
          </button>
          <div class="brand">{{ currentPageName }}</div>
        </div>
        <div class="actions">
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

      <main ref="canvasRef" class="canvas" :class="{ editing: editMode }">
        <GridLayout
          v-model:layout="store.items"
          :col-num="12"
          :row-height="60"
          :is-draggable="editMode"
          :is-resizable="editMode"
          :vertical-compact="true"
          :margin="[12, 12]"
          :responsive="true"
          :breakpoints="breakpoints"
          :cols="cols"
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
const canvasRef = ref<HTMLElement | null>(null)
const defs = ref<WidgetDefinition[]>([])
const panelOpen = ref(false)
const configIdx = ref<number | null>(null)
const editMode = ref(false)
const settingsOpen = ref(false)
const sidebarCollapsed = ref(false)
const creating = ref(false)
const createValue = ref('')
const renamingId = ref<number | null>(null)
const renameValue = ref('')
let saveTimer: number | undefined

// 响应式断点：窄屏自动减少网格列数，避免组件被挤压
const breakpoints = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 }
const cols = { lg: 12, md: 10, sm: 6, xs: 4, xxs: 2 }

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
  // 支持 /?page=97 直接打开某一页：可以把常驻页面设成浏览器书签或启动页
  const wanted = Number(new URLSearchParams(location.search).get('page'))
  if (Number.isFinite(wanted) && pagesStore.pages.some((p) => p.id === wanted)) {
    pagesStore.select(wanted)
  }
  if (pagesStore.currentId !== null) {
    await store.load(pagesStore.currentId)
    syncUrl(pagesStore.currentId)
  }
  try {
    defs.value = await fetchWidgets()
  } catch {
    /* 目录加载失败不阻塞工作台 */
  }
})

/** 把当前页写回地址栏，刷新或直接分享链接都能落到同一页 */
function syncUrl(id: number) {
  const url = new URL(location.href)
  if (url.searchParams.get('page') === String(id)) return
  url.searchParams.set('page', String(id))
  history.replaceState(null, '', url)
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

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
  syncUrl(id)
  await store.load(id)
  // 换页后回到顶部：上一个页面的滚动位置对新页面没有意义
  resetScroll()
}

/** 把画布滚动位置归零 */
function resetScroll() {
  canvasRef.value?.scrollTo({ top: 0 })
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
  syncUrl(page.id)
  await store.load(page.id)
  resetScroll()
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
    syncUrl(pagesStore.currentId)
    await store.load(pagesStore.currentId)
  } else {
    store.items = []
  }
  resetScroll()
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
  // 仅在桌面基准断点(lg)下持久化，避免响应式缩放产生的临时布局覆盖后端基准布局
  if (pagesStore.currentId !== null && window.innerWidth >= 1200) {
    store.save(pagesStore.currentId)
  }
}
</script>
