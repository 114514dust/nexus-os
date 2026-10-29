<template>
  <div class="algo-widget">
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
            class="algo-title"
            :href="q.leetcodeUrl"
            target="_blank"
            rel="noopener"
            :title="q.leetcodeUrl"
            >{{ q.title }}</a
          >
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
          <button class="algo-note-btn" :class="{ has: q.note }" title="笔记" @click="toggleNote(q)">
            📝
          </button>
          <button class="algo-del" title="删除" @click="remove(q)">×</button>
        </div>
        <div v-if="expandedId === q.id" class="algo-note">
          <textarea
            :value="q.note ?? ''"
            placeholder="记录解题思路 / 代码…"
            @input="onNote(q, ($event.target as HTMLTextAreaElement).value)"
          ></textarea>
        </div>
      </div>
      <div v-if="store.items.length === 0" class="empty">暂无题目，点击「＋ 添加」录入，或调整筛选条件</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAlgorithmStore } from '@/stores/algorithms'
import { toastError, friendlyError } from '@/utils/toast'
import type { AlgorithmQuestion } from '@/types'

defineProps<{ config?: Record<string, any> }>()

const store = useAlgorithmStore()

const keyword = ref('')
const difficulty = ref('all')
const sort = ref<'frequency' | 'star' | 'recent'>('frequency')
const showAdd = ref(false)
const newTitle = ref('')
const newUrl = ref('')
const newDifficulty = ref('medium')
const expandedId = ref<number | null>(null)

const noteTimers = new Map<number, number>()

const canAdd = computed(() => newTitle.value.trim() !== '' && newUrl.value.trim() !== '')

onMounted(() => reload())

function reload() {
  store.load({ keyword: keyword.value.trim() || undefined, difficulty: difficulty.value, sort: sort.value })
}

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch([difficulty, sort], reload)

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

function toggleNote(q: AlgorithmQuestion) {
  expandedId.value = expandedId.value === q.id ? null : q.id
}

function onNote(q: AlgorithmQuestion, value: string) {
  const item = store.items.find((i) => i.id === q.id)
  if (item) item.note = value
  const t = noteTimers.get(q.id)
  if (t) window.clearTimeout(t)
  noteTimers.set(q.id, window.setTimeout(() => store.setNote(q.id, value), 600))
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
