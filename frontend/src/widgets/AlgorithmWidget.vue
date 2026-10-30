<template>
  <div class="algo-widget">
    <!-- 内嵌模式：不离开工作台，直接在组件里看题 -->
    <div v-if="embed" class="algo-embed">
      <div class="algo-embed-bar">
        <button class="btn back" title="返回题目列表" @click="closeEmbed">← 返回列表</button>
        <span class="algo-embed-title" :title="embed.leetcodeUrl">{{ embed.title }}</span>
        <a class="btn external" :href="embed.leetcodeUrl" target="_blank" rel="noopener">
          新窗口打开 ↗
        </a>
      </div>
      <iframe
        class="algo-embed-frame"
        :src="embed.leetcodeUrl"
        :title="embed.title"
        loading="lazy"
        referrerpolicy="no-referrer-when-downgrade"
      ></iframe>
      <div class="algo-embed-hint">
        部分站点（如 leetcode.com）禁止被嵌入，显示空白时点右上角「新窗口打开」。
      </div>
    </div>

    <template v-else>
      <div class="algo-toolbar">
        <input v-model="keyword" class="algo-search" placeholder="搜索题目…" />
        <select v-model="difficulty" class="algo-select">
          <option value="all">全部难度</option>
          <option value="easy">简单</option>
          <option value="medium">中等</option>
          <option value="hard">困难</option>
        </select>
        <select v-model="sort" class="algo-select">
          <option value="frequency">高频优先</option>
          <option value="star">星级优先</option>
          <option value="recent">最近更新</option>
        </select>
        <button class="btn algo-add-btn" @click="showAdd = !showAdd">＋ 添加</button>
      </div>

      <div v-if="showAdd" class="algo-add-form">
        <input v-model="newTitle" placeholder="题目标题" />
        <input v-model="newUrl" placeholder="LeetCode 链接" @keyup.enter="add" />
        <select v-model="newDifficulty">
          <option value="easy">简单</option>
          <option value="medium">中等</option>
          <option value="hard">困难</option>
        </select>
        <button class="btn primary" :disabled="!canAdd" @click="add">确定</button>
      </div>

      <div class="algo-list">
        <div v-for="q in store.items" :key="q.id" class="algo-item">
          <div class="algo-row">
            <span class="algo-diff" :class="q.difficulty">{{ diffLabel(q.difficulty) }}</span>
            <a
              v-if="openInNewTab"
              class="algo-title"
              :href="q.leetcodeUrl"
              target="_blank"
              rel="noopener"
              :title="q.leetcodeUrl"
              >{{ q.title }}</a
            >
            <button
              v-else
              class="algo-title algo-title-btn"
              :title="`在组件内打开：${q.leetcodeUrl}`"
              @click="openEmbed(q)"
            >
              {{ q.title }}
            </button>
            <span class="algo-freq" title="面试出现频率">🔥{{ q.frequency }}</span>
            <span class="algo-stars">
              <button
                v-for="n in 5"
                :key="n"
                class="algo-star"
                :class="{ on: n <= q.star }"
                :title="`标记 ${n} 星`"
                @click="setStar(q, n)"
              >
                ★
              </button>
            </span>
            <button class="algo-note-btn" :class="{ has: q.note }" title="笔记" @click="openNote(q)">
              📝
            </button>
            <button class="algo-del" title="删除" @click="remove(q)">×</button>
          </div>
        </div>
        <div v-if="store.items.length === 0" class="empty">暂无题目，点击「＋ 添加」录入，或调整筛选条件</div>
      </div>
    </template>

    <!-- 笔记大弹窗：左右分栏（编辑 + 实时 Markdown 预览），脱离网格约束全屏居中 -->
    <Teleport to="body">
      <div v-if="noteOpen" class="algo-note-mask" @click.self="closeNote">
        <div class="algo-note-modal">
          <div class="algo-note-head">
            <div class="algo-note-title">
              <span v-if="editingQuestion" class="algo-diff" :class="editingQuestion.difficulty">
                {{ diffLabel(editingQuestion.difficulty) }}
              </span>
              <span class="algo-note-name">{{ editingQuestion?.title ?? '' }}</span>
              <a
                v-if="editingQuestion && openInNewTab"
                :href="editingQuestion.leetcodeUrl"
                target="_blank"
                rel="noopener"
                class="algo-note-link"
                >打开题目 ↗</a
              >
              <button
                v-else-if="editingQuestion"
                class="algo-note-link as-btn"
                @click="embedFromNote"
              >
                在组件内打开 →
              </button>
            </div>
            <button class="algo-note-close" title="关闭" @click="closeNote">×</button>
          </div>
          <div class="algo-note-body">
            <div class="algo-note-editor">
              <div class="algo-note-label">编辑（支持 Markdown，代码块用 ```java 等）</div>
              <textarea
                v-model="draftNote"
                placeholder="记录解题思路 / 代码…&#10;&#10;代码块示例：&#10;```java&#10;public int twoSum(int[] nums, int target) {&#10;    return 0;&#10;}&#10;```"
                @keydown="onKeydown"
              ></textarea>
            </div>
            <div class="algo-note-preview">
              <div class="algo-note-label">预览</div>
              <div class="algo-note-rendered markdown-body" v-html="previewHtml"></div>
            </div>
          </div>
          <div class="algo-note-foot">
            <span class="algo-note-tip">{{ saving ? '已自动保存' : '自动保存中…' }}</span>
            <button class="btn primary" @click="closeNote">完成</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAlgorithmStore } from '@/stores/algorithms'
import { useLiveReload } from '@/composables/useLiveReload'
import { renderMarkdown } from '@/utils/markdown'
import { toastError, friendlyError } from '@/utils/toast'
import type { AlgorithmQuestion } from '@/types'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useAlgorithmStore()

/**
 * 题目打开方式：true = 新窗口（默认，LeetCode 官方体验最完整）；
 * false = 在组件内用 iframe 打开，看题不离开工作台。
 */
const openInNewTab = computed(() => props.config?.openInNewTab ?? true)

/** 当前正在组件内嵌浏览的题目，null 表示停留在列表 */
const embed = ref<AlgorithmQuestion | null>(null)

const keyword = ref('')
const difficulty = ref('all')
const sort = ref<'frequency' | 'star' | 'recent'>('frequency')
const showAdd = ref(false)
const newTitle = ref('')
const newUrl = ref('')
const newDifficulty = ref('medium')

// 笔记弹窗状态
const noteOpen = ref(false)
const editingId = ref<number | null>(null)
const draftNote = ref('')
const saving = ref(false)
let noteSaveTimer: number | undefined

const canAdd = computed(() => newTitle.value.trim() !== '' && newUrl.value.trim() !== '')

const editingQuestion = computed(() =>
  store.items.find((i) => i.id === editingId.value)
)

const previewHtml = computed(() => renderMarkdown(draftNote.value || ''))

onMounted(() => reload())

// 题库变更（含 AI 操作）时按当前筛选条件重新拉取
useLiveReload('algorithm', reload)

function reload() {
  store.load({ keyword: keyword.value.trim() || undefined, difficulty: difficulty.value, sort: sort.value })
}

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch([difficulty, sort], reload)

// 笔记内容变化 → 防抖自动保存
watch(draftNote, () => {
  if (editingId.value === null) return
  saving.value = true
  if (noteSaveTimer) window.clearTimeout(noteSaveTimer)
  noteSaveTimer = window.setTimeout(saveNote, 600)
})

async function add() {
  if (!canAdd.value) return
  try {
    await store.add({
      title: newTitle.value.trim(),
      difficulty: newDifficulty.value,
      leetcodeUrl: newUrl.value.trim()
    })
    newTitle.value = ''
    newUrl.value = ''
    showAdd.value = false
  } catch (err) {
    toastError('添加题目失败：' + friendlyError(err))
  }
}

async function setStar(q: AlgorithmQuestion, n: number) {
  const next = q.star === n ? 0 : n
  try {
    await store.setStar(q.id, next)
  } catch (err) {
    toastError('标记星级失败：' + friendlyError(err))
  }
}

function openNote(q: AlgorithmQuestion) {
  editingId.value = q.id
  draftNote.value = q.note ?? ''
  saving.value = false
  noteOpen.value = true
}

function openEmbed(q: AlgorithmQuestion) {
  embed.value = q
}

function closeEmbed() {
  embed.value = null
}

/** 笔记弹窗里点了「在组件内打开」：先关弹窗，再进内嵌视图，不然会被弹窗盖住 */
function embedFromNote() {
  const q = editingQuestion.value
  closeNote()
  if (q) embed.value = q
}

async function saveNote() {
  if (editingId.value === null) return
  try {
    await store.setNote(editingId.value, draftNote.value)
    saving.value = false
  } catch (err) {
    toastError('保存笔记失败：' + friendlyError(err))
  }
}

function closeNote() {
  if (noteSaveTimer) window.clearTimeout(noteSaveTimer)
  if (editingId.value !== null && saving.value) {
    saveNote()
  }
  noteOpen.value = false
  editingId.value = null
  draftNote.value = ''
}

// Tab 键插入缩进（写代码更顺手）
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Tab') return
  e.preventDefault()
  const ta = e.target as HTMLTextAreaElement
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const v = draftNote.value
  draftNote.value = v.substring(0, start) + '  ' + v.substring(end)
  requestAnimationFrame(() => {
    ta.selectionStart = ta.selectionEnd = start + 2
  })
}

async function remove(q: AlgorithmQuestion) {
  try {
    await store.remove(q.id)
  } catch (err) {
    toastError('删除失败：' + friendlyError(err))
  }
}

function diffLabel(d: string) {
  return ({ easy: '简单', medium: '中等', hard: '困难' } as Record<string, string>)[d] ?? d
}
</script>
