<template>
  <div class="posting-widget">
    <!-- 概览：待投 / 新增 / 已加入 / 收藏 -->
    <div class="posting-stats">
      <div class="posting-stat">
        <b>{{ store.stats?.pending ?? 0 }}</b><span>待投递</span>
      </div>
      <div class="posting-stat fresh">
        <b>{{ store.stats?.fresh ?? 0 }}</b><span>近 3 天</span>
      </div>
      <div class="posting-stat applied">
        <b>{{ store.stats?.applied ?? 0 }}</b><span>已加入</span>
      </div>
      <div class="posting-stat">
        <b>{{ store.stats?.starred ?? 0 }}</b><span>收藏</span>
      </div>
    </div>

    <div class="posting-toolbar">
      <input v-model="keyword" class="posting-search" placeholder="搜索公司 / 岗位 / 城市…" />
      <select v-model="category" class="posting-select" title="批次类型">
        <option value="all">全部类型</option>
        <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <select v-model="scope" class="posting-select" title="显示范围">
        <option value="pending">待处理</option>
        <option value="applied">已加入</option>
        <option value="all">全部</option>
      </select>
      <button
        class="posting-chip"
        :class="{ on: onlyMatched }"
        title="只看命中关注关键词的岗位"
        @click="onlyMatched = !onlyMatched"
      >
        ★ 匹配
      </button>
      <button class="posting-chip" :class="{ on: prefsOpen }" title="岗位偏好设置" @click="prefsOpen = !prefsOpen">
        ⚙
      </button>
      <button class="posting-chip" :disabled="store.refreshing" title="从秋招网抓取最新岗位" @click="refresh">
        {{ store.refreshing ? '抓取中…' : '⟳' }}
      </button>
      <button class="btn posting-add-btn" @click="toggleAdd">＋ 岗位</button>
    </div>

    <!-- 偏好：关注关键词决定「匹配」标，也决定早上的推送内容 -->
    <div v-if="prefsOpen" class="posting-prefs">
      <div class="posting-prefs-row">
        <label>关注关键词</label>
        <input
          v-model="focusInput"
          class="posting-search"
          placeholder="逗号分隔，如：Java,后端,算法,数据"
          @keyup.enter="savePrefs"
        />
      </div>
      <div class="posting-prefs-row">
        <label class="posting-check">
          <input v-model="autoFetch" type="checkbox" />
          <span>每天 7:30 自动抓取</span>
        </label>
        <label class="posting-check">
          <span>抓取页数</span>
          <input v-model.number="pages" class="posting-pages" type="number" min="1" max="5" />
        </label>
        <button class="btn posting-add-btn" @click="savePrefs">保存</button>
      </div>
    </div>

    <!-- 手动添加：内推群、公众号、学院就业网这些抓不到的渠道 -->
    <div v-if="addOpen" class="posting-add-form">
      <input v-model="form.company" class="posting-search" placeholder="公司名（必填）" @keyup.enter="submitAdd" />
      <input v-model="form.positions" class="posting-search" placeholder="岗位方向，多个用、分隔" @keyup.enter="submitAdd" />
      <input v-model="form.city" class="posting-city" placeholder="城市" @keyup.enter="submitAdd" />
      <select v-model="form.category" class="posting-select">
        <option v-for="c in CATEGORIES" :key="c.key" :value="c.key">{{ c.label }}</option>
      </select>
      <input v-model="form.url" class="posting-search" placeholder="投递 / 公告链接" @keyup.enter="submitAdd" />
      <button class="btn posting-add-btn" @click="submitAdd">保存</button>
    </div>

    <div class="posting-list">
      <article
        v-for="p in store.items"
        :key="p.id"
        class="posting-card"
        :class="[p.category, { starred: p.starred, matched: p.matched, done: p.jobId }]"
      >
        <div class="posting-row-1">
          <span v-if="p.fresh" class="posting-new" title="近 3 天新入库">NEW</span>
          <span class="posting-company" :title="p.title">{{ p.company }}</span>
          <span v-if="p.companyType" class="posting-type" :class="typeClass(p.companyType)">
            {{ p.companyType }}
          </span>
          <span v-if="p.batch" class="posting-batch">{{ p.batch }}</span>
          <button
            class="posting-star"
            :class="{ on: p.starred }"
            :title="p.starred ? '取消收藏' : '收藏'"
            @click="star(p)"
          >
            ★
          </button>
        </div>

        <div class="posting-positions" :title="p.positions || p.title">{{ p.positions || p.title }}</div>

        <div class="posting-row-2">
          <span v-if="p.city" class="posting-city-text" :title="p.city">📍{{ p.city }}</span>
          <span v-if="p.deadlineText" class="posting-deadline">⏱{{ p.deadlineText }}</span>
          <span class="posting-date">{{ fmtDate(p.publishedAt) }}</span>
          <span v-if="p.matched" class="posting-match" :title="'命中：' + p.matchKeywords.join('、')">
            ★ {{ p.matchKeywords.slice(0, 2).join('/') }}
          </span>
        </div>

        <div class="posting-row-3">
          <button v-if="!p.jobId" class="posting-apply" @click="apply(p)">加入追踪</button>
          <span v-else class="posting-done" title="已加入求职追踪">✓ 已加入</span>
          <a
            v-if="p.announceUrl"
            class="posting-link"
            :href="p.announceUrl"
            target="_blank"
            rel="noopener noreferrer"
            title="打开招聘方官方公告"
            >公告 ↗</a
          >
          <a
            v-else-if="p.detailUrl"
            class="posting-link"
            :href="p.detailUrl"
            target="_blank"
            rel="noopener noreferrer"
            >详情 ↗</a
          >
          <span class="posting-spacer"></span>
          <button class="posting-ignore" title="不再显示这条" @click="ignore(p)">忽略</button>
        </div>
      </article>

      <div v-if="store.items.length === 0 && !store.loading" class="posting-empty muted">
        <template v-if="scope === 'pending'">
          还没有岗位。点右上角 <b>⟳</b> 从秋招网抓一批（约 30 秒），或在「已加入」里看已处理的。
        </template>
        <template v-else>这个范围下暂时没有记录。</template>
      </div>
    </div>

    <footer class="posting-foot">
      <a href="https://qiuzhaowang.com/" target="_blank" rel="noopener noreferrer">秋招网 ↗</a>
      <span v-if="store.stats?.lastFetchedAt" class="muted">
        上次抓取 {{ fmtTime(store.stats.lastFetchedAt) }}
      </span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { usePostingStore } from '@/stores/postings'
import { useLiveReload } from '@/composables/useLiveReload'
import { friendlyError, toastError, toastSuccess } from '@/utils/toast'
import type { JobCategory, JobPosting } from '@/types'

const store = usePostingStore()

const keyword = ref('')
const category = ref('all')
const scope = ref<'pending' | 'applied' | 'all'>('pending')
const onlyMatched = ref(false)

const prefsOpen = ref(false)
const focusInput = ref('')
const autoFetch = ref(true)
const pages = ref(2)

const addOpen = ref(false)
const form = reactive({
  company: '',
  positions: '',
  city: '',
  url: '',
  category: 'autumn' as JobCategory
})

const CATEGORIES: { key: string; label: string }[] = [
  { key: 'autumn', label: '秋招' },
  { key: 'spring', label: '春招' },
  { key: 'intern', label: '实习' },
  { key: 'social', label: '社招' },
  { key: 'soe', label: '国企' },
  { key: 'other', label: '其他' }
]

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch([category, scope, onlyMatched], () => reload())

onMounted(() => {
  reload()
  loadPrefs()
})

// AI 对话抓取、定时抓取都会改这批数据，统一走 SSE 自动刷新
useLiveReload('posting', () => reload())

function reload() {
  store
    .load({
      keyword: keyword.value.trim() || undefined,
      category: category.value,
      scope: scope.value,
      onlyMatched: onlyMatched.value
    })
    .catch((e) => console.warn('[posting] 加载失败', e))
}

async function loadPrefs() {
  try {
    const p = await store.loadPrefs()
    focusInput.value = p.focusKeywords.join(',')
    autoFetch.value = p.autoFetch
    pages.value = p.pages
  } catch (e) {
    console.warn('[posting] 偏好加载失败', e)
  }
}

async function savePrefs() {
  try {
    const keywords = focusInput.value
      .split(/[,，\s]+/)
      .map((k) => k.trim())
      .filter(Boolean)
    await store.savePrefs({ focusKeywords: keywords, autoFetch: autoFetch.value, pages: pages.value })
    prefsOpen.value = false
    reload()
    toastSuccess(keywords.length ? `已保存 ${keywords.length} 个关注关键词` : '已清除关注关键词')
  } catch (e) {
    toastError('保存失败：' + friendlyError(e))
  }
}

async function refresh() {
  try {
    const r = await store.refresh()
    if (!r.ok) {
      toastError(r.message || '抓取失败')
      return
    }
    reload()
    toastSuccess(r.added > 0 ? `抓到 ${r.added} 个新岗位（更新 ${r.updated} 个）` : '没有新岗位')
  } catch (e) {
    toastError('抓取失败：' + friendlyError(e))
  }
}

function toggleAdd() {
  addOpen.value = !addOpen.value
}

async function submitAdd() {
  const company = form.company.trim()
  if (!company) {
    toastError('公司名不能为空')
    return
  }
  try {
    await store.add({
      company,
      positions: form.positions.trim() || undefined,
      city: form.city.trim() || undefined,
      url: form.url.trim() || undefined,
      category: form.category
    })
    form.company = ''
    form.positions = ''
    form.city = ''
    form.url = ''
    addOpen.value = false
    reload()
    toastSuccess(`已添加「${company}」`)
  } catch (e) {
    toastError('添加失败：' + friendlyError(e))
  }
}

async function apply(p: JobPosting) {
  try {
    const r = await store.apply(p.id)
    toastSuccess(
      r.created ? `已加入求职追踪：${r.job.company} · ${r.job.jobTitle}` : `「${r.job.company}」已在求职追踪里了`
    )
  } catch (e) {
    toastError('加入失败：' + friendlyError(e))
  }
}

async function star(p: JobPosting) {
  try {
    await store.setStarred(p.id, !p.starred)
    // 服务端按「收藏 → 匹配 → 新」排序，改了收藏就得重新拉，否则位置不动
    reload()
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function ignore(p: JobPosting) {
  try {
    await store.ignore(p.id)
    toastSuccess(`已忽略「${p.company}」，下次抓取不会再冒出来`)
    reload()
  } catch (e) {
    toastError('忽略失败：' + friendlyError(e))
  }
}

function typeClass(type: string) {
  if (type.includes('国企')) return 'soe'
  if (type.includes('事业')) return 'gov'
  if (type.includes('合资') || type.includes('外资')) return 'joint'
  return 'private'
}

/** 2026-10-30 → 10/30 */
function fmtDate(d: string | null) {
  if (!d) return ''
  const parts = d.split('-')
  return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : d
}

function fmtTime(s: string | null) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${d.getMonth() + 1}月${d.getDate()}日 ${hh}:${mm}`
}
</script>
