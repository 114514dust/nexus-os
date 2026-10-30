<template>
  <div class="todo-widget">
    <div class="todo-input">
      <input v-model="text" placeholder="添加待办，回车确认" @keyup.enter="add" />
      <input v-model="newDue" type="date" class="todo-new-due" title="到期日（可不填）" />
      <button class="btn" @click="add">添加</button>
    </div>

    <ul class="todo-list">
      <li v-for="t in visibleItems" :key="t.id" :class="{ done: t.done }">
        <input type="checkbox" :checked="t.done" @change="toggle(t)" />
        <span class="todo-text">{{ t.content }}</span>

        <!-- 点开才编辑日期：平时只占一个角标，不抢待办正文的空间 -->
        <span v-if="editingDue === t.id" class="todo-due-edit">
          <input
            :data-due-for="t.id"
            :value="dueDraft"
            type="date"
            @change="applyDue(t, $event)"
            @keyup.esc="editingDue = null"
          />
          <button class="todo-due-clear" title="清除到期日" @click="clearDue(t)">清除</button>
          <button class="todo-due-done" title="收起" @click="editingDue = null">✓</button>
        </span>
        <button
          v-else
          class="todo-due"
          :class="[t.done ? '' : dueClass(t), { empty: !t.dueDate }]"
          :title="t.dueDate ? '到期日：' + t.dueDate + '（点击修改）' : '设置到期日'"
          @click="startDueEdit(t)"
        >
          {{ dueLabel(t) }}
        </button>

        <button class="del" title="删除" @click="remove(t)">×</button>
      </li>
      <li v-if="visibleItems.length === 0" class="empty">暂无待办</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useTodoStore } from '@/stores/todos'
import { useLiveReload } from '@/composables/useLiveReload'
import { toastError, friendlyError } from '@/utils/toast'
import type { Todo } from '@/types'

const props = defineProps<{ config?: Record<string, any> }>()

const store = useTodoStore()
const text = ref('')
/** 新建时填的到期日（可选） */
const newDue = ref('')
/** 正在编辑到期日的那条待办 id */
const editingDue = ref<number | null>(null)
const dueDraft = ref('')

const showDone = computed(() => props.config?.showDone ?? true)

/**
 * 排序：未完成在前，其余按到期日升序（没有日期的沉底）。
 * 到期提醒的意义就是「今天该做的浮到最上面」，所以排序放前端做，不改后端默认顺序。
 */
const visibleItems = computed(() => {
  const base = showDone.value ? store.items : store.items.filter((t) => !t.done)
  return [...base].sort((a, b) => {
    if (a.done !== b.done) return a.done ? 1 : -1
    if (!a.dueDate && !b.dueDate) return 0
    if (!a.dueDate) return 1
    if (!b.dueDate) return -1
    return a.dueDate.localeCompare(b.dueDate)
  })
})

onMounted(() => store.load())

// AI 对话调工具、或其它页面改了待办时，自动拉取最新数据
useLiveReload('todo', () => store.load())

async function add() {
  const v = text.value.trim()
  if (!v) return
  try {
    await store.add(v, newDue.value || null)
    text.value = ''
    newDue.value = ''
  } catch (err) {
    toastError('添加待办失败：' + friendlyError(err))
  }
}

async function toggle(t: Todo) {
  try {
    await store.toggle(t)
  } catch (err) {
    // 复选框状态已经先变了，失败要重新拉一次把界面拉回真实状态，否则界面在骗人
    toastError('更新待办失败：' + friendlyError(err))
    await store.load().catch(() => {})
  }
}

async function remove(t: Todo) {
  try {
    await store.remove(t.id)
  } catch (err) {
    toastError('删除待办失败：' + friendlyError(err))
  }
}

// ---- 到期日 ----

function startDueEdit(t: Todo) {
  editingDue.value = t.id
  dueDraft.value = t.dueDate ?? ''
  nextTick(() => {
    const el = document.querySelector<HTMLInputElement>(`input[data-due-for="${t.id}"]`)
    el?.focus()
    try {
      el?.showPicker?.()
    } catch {
      // 浏览器要求必须由用户手势直接触发，nextTick 之后可能已失效 —— 退化成一个普通日期输入框
    }
  })
}

async function applyDue(t: Todo, e: Event) {
  const value = (e.target as HTMLInputElement).value
  if (!value) {
    await clearDue(t)
    return
  }
  try {
    await store.setDueDate(t, value)
    editingDue.value = null
  } catch (err) {
    toastError('设置到期日失败：' + friendlyError(err))
  }
}

async function clearDue(t: Todo) {
  try {
    await store.setDueDate(t, '')
    editingDue.value = null
  } catch (err) {
    toastError('清除到期日失败：' + friendlyError(err))
  }
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

function dayDiff(dateStr: string) {
  const d = new Date(dateStr + 'T00:00:00')
  return Math.round((startOfDay(d) - startOfDay(new Date())) / 86400000)
}

function dueClass(t: Todo) {
  if (!t.dueDate) return ''
  const diff = dayDiff(t.dueDate)
  if (diff < 0) return 'overdue'
  if (diff === 0) return 'today'
  if (diff === 1) return 'tomorrow'
  if (diff <= 3) return 'soon'
  return ''
}

/** 「逾期 3 天 / 今天 / 明天 / 3 天后 / 11月2日」，没日期时只留一个小日历图标 */
function dueLabel(t: Todo) {
  if (!t.dueDate) return '📅'
  const diff = dayDiff(t.dueDate)
  if (diff < 0) return `逾期 ${-diff} 天`
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  if (diff <= 3) return `${diff} 天后`
  const [, m, d] = t.dueDate.split('-')
  return `${Number(m)}月${Number(d)}日`
}
</script>

<style scoped>
.empty {
  color: var(--text-2);
  font-size: 13px;
  padding: 4px 0;
}

.todo-input {
  flex-wrap: wrap;
}

.todo-new-due {
  /* 130px 时中文环境的「2026/10/30」会被压成 10/27/周..，150px 起才放得下 */
  width: 150px;
  flex: none !important;
  font-size: 12px;
  padding: 6px 8px;
  /* 深色主题下原生日历图标默认是黑色的，声明配色方案让它跟着主题走 */
  color-scheme: dark;
}

.todo-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-list li.done .todo-text {
  color: var(--text-2);
  text-decoration: line-through;
}

.todo-due {
  flex-shrink: 0;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 5px;
  padding: 1px 5px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-2);
  cursor: pointer;
  white-space: nowrap;
}

/* 没日期的待办常显一个淡图标：完全隐藏就没人知道这里可以设日期 */
.todo-due.empty {
  opacity: 0.4;
  transition: opacity 0.15s;
}

.todo-list li:hover .todo-due.empty,
.todo-due.empty:focus-visible {
  opacity: 1;
}

.todo-due:hover {
  border-color: var(--border);
  color: var(--text);
}

.todo-due.today {
  color: #f0a13c;
  border-color: rgba(240, 161, 60, 0.5);
  background: rgba(240, 161, 60, 0.12);
}

.todo-due.tomorrow,
.todo-due.soon {
  color: #e6c07b;
}

.todo-due.overdue {
  color: var(--danger);
  border-color: rgba(226, 85, 85, 0.5);
  background: rgba(226, 85, 85, 0.12);
}

.todo-due-edit {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.todo-due-edit input {
  background: var(--surface-2);
  border: 1px solid var(--accent);
  border-radius: 5px;
  color: var(--text);
  padding: 2px 4px;
  font-size: 11px;
  max-width: 122px;
}

.todo-due-clear,
.todo-due-done {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 11px;
  cursor: pointer;
  padding: 0 2px;
}

.todo-due-clear:hover {
  color: var(--danger);
}

.todo-due-done:hover {
  color: var(--accent);
}
</style>
