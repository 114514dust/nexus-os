<template>
  <div class="job-widget">
    <!-- 概览：数字不大，但每天都想知道自己走到哪了 -->
    <div class="job-stats">
      <div class="job-stat">
        <b>{{ store.stats?.total ?? 0 }}</b><span>投递</span>
      </div>
      <div class="job-stat">
        <b>{{ store.stats?.active ?? 0 }}</b><span>进行中</span>
      </div>
      <div class="job-stat interviewing">
        <b>{{ store.stats?.interviewing ?? 0 }}</b><span>面试中</span>
      </div>
      <div class="job-stat offer">
        <b>{{ store.stats?.offers ?? 0 }}</b><span>Offer</span>
      </div>
      <div class="job-stat">
        <b>{{ store.stats?.replyRate ?? 0 }}%</b><span>回复率</span>
      </div>
    </div>

    <div class="job-toolbar">
      <input v-model="keyword" class="job-search" placeholder="搜索公司 / 岗位…" />
      <select v-model="category" class="job-select" title="投递类型">
        <option value="all">全部类型</option>
        <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <select v-model="scope" class="job-select" title="显示范围">
        <option value="active">进行中</option>
        <option value="closed">已结束</option>
        <option value="all">全部</option>
      </select>
      <select v-model="sort" class="job-select" title="排序">
        <option value="recent">最近更新</option>
        <option value="due">面试临近</option>
        <option value="company">按公司</option>
      </select>
      <button class="btn job-add-btn" @click="toggleAdd">＋ 投递</button>
    </div>

    <!-- 快速录入：公司名回车即可建一条，先把「投了哪家」记下来，细节之后再补 -->
    <div v-if="showAdd" class="job-add-form">
      <input v-model="newCompany" placeholder="公司名称（回车添加）" @keyup.enter="add" />
      <input v-model="newTitle" placeholder="岗位（可选）" @keyup.enter="add" />
      <select v-model="newCategory" class="job-select">
        <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <button class="btn primary" :disabled="!canAdd" @click="add">添加</button>
    </div>

    <div class="job-list">
      <article
        v-for="job in store.items"
        :key="job.id"
        class="job-card"
        :class="[job.stage, { starred: job.starred }]"
        @click="openDetail(job)"
      >
        <div class="job-row-1">
          <button
            class="job-star"
            :class="{ on: job.starred }"
            :title="job.starred ? '取消重点' : '标记为重点'"
            @click.stop="toggleStar(job)"
          >
            {{ job.starred ? '★' : '☆' }}
          </button>
          <span class="job-company">{{ job.company }}</span>
          <span v-if="job.jobTitle" class="job-role">{{ job.jobTitle }}</span>
          <span class="job-stage" :class="job.stage">{{ stageLabel(job.stage) }}</span>
        </div>

        <div class="job-row-2">
          <span class="job-cat" :class="job.category">{{ catLabel(job.category) }}</span>
          <span v-if="job.nextAt || job.nextAction" class="job-due" :class="dueClass(job)">
            {{ dueText(job) }}
          </span>
          <span v-else class="job-idle">{{ idleText(job) }}</span>
          <a
            v-if="job.url"
            class="job-link"
            :href="job.url"
            target="_blank"
            rel="noopener"
            title="打开投递页面"
            @click.stop
            >投递页 ↗</a
          >
        </div>

        <div v-if="canAdvance(job) || followUp(job)" class="job-row-3">
          <button v-if="canAdvance(job)" class="job-advance" @click.stop="advance(job)">
            推进 → {{ stageLabel(nextStage(job.stage)!) }}
          </button>
          <span v-if="followUp(job)" class="job-nudge">{{ daysSince(job.updatedAt) }} 天没动静，可以催一下</span>
        </div>
      </article>

      <div v-if="!store.loading && store.items.length === 0" class="empty job-empty">
        {{ emptyText }}
      </div>
    </div>

    <!-- 详情弹窗：进度 + 字段编辑 + 时间线，左右分栏承载体量 -->
    <Teleport to="body">
      <div v-if="detail && draft" class="job-mask" @click.self="closeDetail">
        <div class="job-modal">
          <div class="job-modal-head">
            <div class="job-modal-title">
              <span class="job-stage" :class="draft.stage">{{ stageLabel(draft.stage) }}</span>
              <span class="job-modal-company">{{ draft.company || '未命名公司' }}</span>
              <span v-if="draft.jobTitle" class="job-modal-role">{{ draft.jobTitle }}</span>
            </div>
            <div class="job-modal-head-tools">
              <a
                v-if="draft.url"
                :href="draft.url"
                target="_blank"
                rel="noopener"
                class="job-modal-link"
                >投递页 ↗</a
              >
              <button class="job-modal-close" title="关闭" @click="closeDetail">×</button>
            </div>
          </div>

          <!-- 阶段条：点一下就是当前进度，走过的阶段点亮 -->
          <div class="job-steps">
            <button
              v-for="(s, i) in STAGES"
              :key="s.key"
              class="job-step"
              :class="{ current: draft.stage === s.key, done: stepIndex(draft.stage) > i }"
              @click="switchStage(s.key)"
            >
              {{ s.label }}
            </button>
          </div>
          <div class="job-steps-sub">
            <button
              class="job-fail"
              :class="{ current: draft.stage === 'rejected' }"
              @click="switchStage('rejected')"
            >
              标记未通过
            </button>
            <button v-if="canAdvance(draft)" class="btn primary job-modal-advance" @click="advanceDraft">
              推进到 {{ stageLabel(nextStage(draft.stage)!) }}
            </button>
          </div>

          <div class="job-modal-body">
            <div class="job-form">
              <div class="job-field">
                <label>公司</label>
                <input v-model="draft.company" placeholder="公司名称" />
              </div>
              <div class="job-field">
                <label>岗位</label>
                <input v-model="draft.jobTitle" placeholder="如：Java 后端开发" />
              </div>
              <div class="job-field">
                <label>类型</label>
                <select v-model="draft.category">
                  <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
                </select>
              </div>
              <div class="job-field">
                <label>投递链接</label>
                <input v-model="draft.url" placeholder="https://…" />
              </div>
              <div class="job-field">
                <label>下一步</label>
                <input v-model="draft.nextAction" placeholder="如：准备项目介绍 / 等 HR 约面" />
              </div>
              <div class="job-field">
                <label>日期</label>
                <div class="job-date">
                  <input v-model="draft.nextAt" type="date" />
                  <button v-if="draft.nextAt" class="job-clear" @click="draft.nextAt = ''">清除</button>
                </div>
              </div>
              <div class="job-field">
                <label>重点</label>
                <label class="job-check">
                  <input v-model="draft.starred" type="checkbox" />
                  置顶显示
                </label>
              </div>
              <div class="job-field job-note-field">
                <div class="job-note-head">
                  <label>复盘笔记</label>
                  <button class="job-note-toggle" @click="notePreview = !notePreview">
                    {{ notePreview ? '编辑' : '预览' }}
                  </button>
                </div>
                <textarea
                  v-if="!notePreview"
                  v-model="draft.note"
                  placeholder="面试问了什么、哪里答得好、下次怎么改…（支持 Markdown）"
                ></textarea>
                <div v-else class="job-note-preview markdown-body" v-html="noteHtml"></div>
              </div>
            </div>

            <div class="job-timeline">
              <div class="job-timeline-title">进展记录</div>
              <div class="job-timeline-add">
                <input v-model="noteInput" placeholder="记一笔，回车提交" @keyup.enter="submitNote" />
                <button class="btn" :disabled="!noteInput.trim()" @click="submitNote">记录</button>
              </div>
              <ul class="job-events">
                <li v-for="e in detail.events" :key="e.id">
                  <span class="job-event-dot" :class="e.stage"></span>
                  <div class="job-event-main">
                    <div class="job-event-head">
                      <span class="job-event-stage">{{ stageLabel(e.stage) }}</span>
                      <span class="job-event-time">{{ fmtTime(e.occurredAt) }}</span>
                    </div>
                    <div v-if="e.note" class="job-event-note">{{ e.note }}</div>
                  </div>
                </li>
                <li v-if="detail.events.length === 0" class="empty">还没有进展记录</li>
              </ul>
            </div>
          </div>

          <div class="job-modal-foot">
            <button class="job-modal-del" @click="removeDraft">删除记录</button>
            <div class="job-modal-foot-right">
              <span v-if="dirty" class="job-dirty">● 未保存</span>
              <button class="btn" @click="closeDetail">关闭</button>
              <button class="btn primary" :disabled="saving" @click="save">保存</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useJobStore } from '@/stores/jobs'
import { useLiveReload } from '@/composables/useLiveReload'
import { renderMarkdown } from '@/utils/markdown'
import { toastError, toastSuccess, friendlyError } from '@/utils/toast'
import type { JobApplication, JobCategory, JobDetail, JobStage } from '@/types'

defineProps<{ config?: Record<string, any> }>()

/** 线性阶段（不含「未通过」，它是旁路终态，单独一个按钮） */
const STAGES: { key: JobStage; label: string }[] = [
  { key: 'wish', label: '想投' },
  { key: 'applied', label: '已投递' },
  { key: 'written', label: '笔试' },
  { key: 'interview1', label: '一面' },
  { key: 'interview2', label: '二面' },
  { key: 'hr', label: 'HR 面' },
  { key: 'offer', label: 'Offer' },
  { key: 'accepted', label: '已入职' }
]

const CATEGORIES: { key: JobCategory; label: string }[] = [
  { key: 'autumn', label: '秋招' },
  { key: 'spring', label: '春招' },
  { key: 'intern', label: '实习' },
  { key: 'social', label: '社招' },
  { key: 'soe', label: '国企' },
  { key: 'other', label: '其他' }
]

/** 投递 7 天没更新就提示可以跟进 —— 秋招里很多机会是催出来的 */
const FOLLOW_UP_DAYS = 7

interface JobDraft {
  id: number
  company: string
  jobTitle: string
  category: JobCategory
  stage: JobStage
  url: string
  nextAction: string
  nextAt: string
  starred: boolean
  note: string
}

const store = useJobStore()

const keyword = ref('')
const category = ref('all')
const scope = ref<'active' | 'closed' | 'all'>('active')
const sort = ref<'recent' | 'due' | 'company'>('recent')

const showAdd = ref(false)
const newCompany = ref('')
const newTitle = ref('')
const newCategory = ref<JobCategory>('autumn')
const canAdd = computed(() => newCompany.value.trim() !== '')

const detail = ref<JobDetail | null>(null)
const draft = ref<JobDraft | null>(null)
const snapshot = ref('')
const saving = ref(false)
const notePreview = ref(false)
const noteInput = ref('')

const noteHtml = computed(() => renderMarkdown(draft.value?.note ?? ''))

const dirty = computed(() => !!draft.value && JSON.stringify(draft.value) !== snapshot.value)

const emptyText = computed(() => {
  if (keyword.value.trim()) return '没有匹配的投递记录，换个关键词试试'
  if (scope.value === 'closed') return '还没有已结束的记录'
  return '还没有投递记录。点「＋ 投递」记下第一家，之后点一下就推进进度'
})

onMounted(() => reload())

// AI 对话或其它入口改了数据，这个组件自动跟上
useLiveReload('job', () => reload())

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch([category, scope, sort], () => reload())

function reload() {
  store
    .load({
      keyword: keyword.value.trim() || undefined,
      category: category.value,
      scope: scope.value,
      sort: sort.value
    })
    .catch((e) => console.warn('[job] 加载失败', e))
}

// ---- 阶段 / 类型文案 ----

function stageLabel(stage: string) {
  if (stage === 'rejected') return '未通过'
  return STAGES.find((s) => s.key === stage)?.label ?? stage
}

function catLabel(c: string) {
  return CATEGORIES.find((x) => x.key === c)?.label ?? '其他'
}

function stepIndex(stage: string) {
  return STAGES.findIndex((s) => s.key === stage)
}

function nextStage(stage: string): JobStage | null {
  const idx = stepIndex(stage)
  if (idx < 0 || idx === STAGES.length - 1) return null
  return STAGES[idx + 1].key
}

function canAdvance(job: { stage: string }) {
  return nextStage(job.stage) !== null
}

// ---- 时间计算 ----

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

function dayDiff(dateStr: string) {
  const d = new Date(dateStr + 'T00:00:00')
  return Math.round((startOfDay(d) - startOfDay(new Date())) / 86400000)
}

function daysSince(iso: string) {
  const d = new Date(iso)
  return Math.max(0, Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000))
}

function fmtDate(dateStr: string) {
  const [, m, d] = dateStr.split('-')
  return `${Number(m)}月${Number(d)}日`
}

function fmtTime(iso: string) {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function dueClass(job: JobApplication) {
  if (!job.nextAt) return ''
  const diff = dayDiff(job.nextAt)
  if (diff < 0) return 'overdue'
  if (diff === 0) return 'today'
  if (diff === 1) return 'tomorrow'
  if (diff <= 3) return 'soon'
  return ''
}

/** 「11月2日 · 还有 3 天 · 准备自我介绍」 */
function dueText(job: JobApplication) {
  const parts: string[] = []
  if (job.nextAt) {
    const diff = dayDiff(job.nextAt)
    const rel = diff < 0 ? `已过 ${-diff} 天` : diff === 0 ? '今天' : diff === 1 ? '明天' : `还有 ${diff} 天`
    parts.push(`${fmtDate(job.nextAt)}`, rel)
  }
  if (job.nextAction) parts.push(job.nextAction)
  // 有日期用倒计时图标，只有待办文字时用图钉，避免「⏱ 等笔试通知」这种怪组合
  return (job.nextAt ? '⏱ ' : '📌 ') + parts.join(' · ')
}

function idleText(job: JobApplication) {
  const days = daysSince(job.updatedAt)
  return days === 0 ? '今天更新' : `${days} 天前更新`
}

/** 长期没有进展：Offer / 结束态不用催 */
function followUp(job: JobApplication) {
  if (job.stage === 'offer' || job.stage === 'accepted' || job.stage === 'rejected') return false
  return daysSince(job.updatedAt) >= FOLLOW_UP_DAYS
}

// ---- 列表操作 ----

function toggleAdd() {
  showAdd.value = !showAdd.value
  if (showAdd.value) newCompany.value = ''
}

async function add() {
  if (!canAdd.value) return
  try {
    await store.add({
      company: newCompany.value.trim(),
      jobTitle: newTitle.value.trim(),
      category: newCategory.value
    })
    newCompany.value = ''
    newTitle.value = ''
    reload()
  } catch (e) {
    toastError('添加投递失败：' + friendlyError(e))
  }
}

async function toggleStar(job: JobApplication) {
  try {
    await store.update(job.id, { starred: !job.starred })
  } catch (e) {
    toastError('更新失败：' + friendlyError(e))
  }
}

async function advance(job: JobApplication) {
  const next = nextStage(job.stage)
  if (!next) return
  try {
    await store.advance(job.id)
    toastSuccess(`已推进到「${stageLabel(next)}」`)
  } catch (e) {
    toastError('推进失败：' + friendlyError(e))
  }
}

// ---- 详情弹窗 ----

async function openDetail(job: JobApplication) {
  try {
    const d = await store.detail(job.id)
    detail.value = d
    fillDraft(d.job)
    noteInput.value = ''
    notePreview.value = false
  } catch (e) {
    toastError('打开详情失败：' + friendlyError(e))
  }
}

function fillDraft(job: JobApplication) {
  draft.value = {
    id: job.id,
    company: job.company,
    jobTitle: job.jobTitle ?? '',
    category: job.category,
    stage: job.stage,
    url: job.url ?? '',
    nextAction: job.nextAction ?? '',
    nextAt: job.nextAt ?? '',
    starred: job.starred,
    note: job.note ?? ''
  }
  snapshot.value = JSON.stringify(draft.value)
}

function closeDetail() {
  if (dirty.value && !window.confirm('有未保存的修改，确定关闭吗？')) return
  detail.value = null
  draft.value = null
}

/** 阶段条点击：立即生效（这是最高频操作，不该还要点一次保存） */
async function switchStage(stage: JobStage) {
  if (!draft.value || draft.value.stage === stage) return
  try {
    await store.update(draft.value.id, { stage })
    draft.value.stage = stage
    patchSnapshot('stage', stage)
    await refreshTimeline()
  } catch (e) {
    toastError('更新进度失败：' + friendlyError(e))
  }
}

async function advanceDraft() {
  if (!draft.value) return
  const next = nextStage(draft.value.stage)
  if (!next) return
  try {
    await store.advance(draft.value.id)
    draft.value.stage = next
    patchSnapshot('stage', next)
    await refreshTimeline()
    toastSuccess(`已推进到「${stageLabel(next)}」`)
  } catch (e) {
    toastError('推进失败：' + friendlyError(e))
  }
}

/**
 * 阶段变化后只刷新时间线。
 * 注意不能整份覆盖 draft —— 用户可能正在改岗位名，刷新一次不该把它冲掉；
 * 表单里剩下的修改仍然算「未保存」，由用户自己决定是否保存。
 */
async function refreshTimeline() {
  if (!draft.value) return
  const d = await store.detail(draft.value.id)
  detail.value = d
  if (draft.value) draft.value.stage = d.job.stage
}

/** 已落库的字段同步进快照，避免「未保存」误提示（JSON 键序与 draft 一致） */
function patchSnapshot(key: keyof JobDraft, value: unknown) {
  if (!snapshot.value) return
  const obj = JSON.parse(snapshot.value)
  obj[key] = value
  snapshot.value = JSON.stringify(obj)
}

async function submitNote() {
  if (!draft.value || !noteInput.value.trim()) return
  try {
    const d = await store.addNote(draft.value.id, noteInput.value.trim())
    detail.value = d
    noteInput.value = ''
  } catch (e) {
    toastError('记录失败：' + friendlyError(e))
  }
}

async function save() {
  if (!draft.value) return
  if (!draft.value.company.trim()) {
    toastError('公司名称不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await store.update(draft.value.id, {
      company: draft.value.company.trim(),
      jobTitle: draft.value.jobTitle,
      category: draft.value.category,
      url: draft.value.url,
      nextAction: draft.value.nextAction,
      starred: draft.value.starred,
      note: draft.value.note,
      // 日期为空时要用显式清除标记：后端约定 null = 不更新
      ...(draft.value.nextAt ? { nextAt: draft.value.nextAt } : { clearNextAt: true })
    })
    if (detail.value) detail.value.job = updated
    fillDraft(updated)
    toastSuccess('已保存')
  } catch (e) {
    toastError('保存失败：' + friendlyError(e))
  } finally {
    saving.value = false
  }
}

async function removeDraft() {
  if (!draft.value) return
  if (!window.confirm(`确定删除「${draft.value.company}」这条投递记录吗？`)) return
  try {
    await store.remove(draft.value.id)
    detail.value = null
    draft.value = null
    toastSuccess('已删除')
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}
</script>
