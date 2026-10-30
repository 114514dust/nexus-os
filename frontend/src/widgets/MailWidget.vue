<template>
  <div class="mail-widget">
    <!-- 未配置：先告诉用户授权码从哪来，否则「IMAP 授权码」这五个字会直接把人劝退 -->
    <div v-if="!configured" class="mail-setup">
      <div class="mail-setup-head">
        <span class="mail-setup-icon">📮</span>
        <div>
          <div class="mail-setup-title">订阅你的 QQ 邮箱</div>
          <div class="mail-setup-sub muted">重要邮件（面试、笔试、缴费截止）会自动变成待办</div>
        </div>
      </div>
      <ol class="mail-steps">
        <li>登录 QQ 邮箱 → 设置 → 账户</li>
        <li>找到「IMAP/SMTP 服务」，开启并按提示发短信验证</li>
        <li>复制生成的 <b>16 位授权码</b>（不是登录密码）</li>
        <li>填到下面的配置里，点「测试连接」</li>
      </ol>
      <div class="mail-setup-actions">
        <a class="mail-help" href="https://mail.qq.com/" target="_blank" rel="noopener noreferrer">
          打开 QQ 邮箱 ↗
        </a>
        <button class="btn mail-setup-btn" @click="settingsOpen = true">填写配置</button>
      </div>
    </div>

    <!-- 概览 -->
    <div v-if="configured" class="mail-stats">
      <div class="mail-stat high">
        <b>{{ store.stats?.high ?? 0 }}</b><span>重要</span>
      </div>
      <div class="mail-stat">
        <b>{{ store.stats?.medium ?? 0 }}</b><span>可关注</span>
      </div>
      <div class="mail-stat done">
        <b>{{ store.stats?.toTodo ?? 0 }}</b><span>已转待办</span>
      </div>
      <div class="mail-stat">
        <b>{{ store.stats?.total ?? 0 }}</b><span>已收录</span>
      </div>
    </div>

    <div class="mail-toolbar">
      <input
        v-if="configured"
        v-model="keyword"
        class="mail-search"
        placeholder="搜索主题 / 发件人…"
      />
      <select v-if="configured" v-model="scope" class="mail-select" title="显示范围">
        <option value="important">重要</option>
        <option value="todo">已转待办</option>
        <option value="all">全部</option>
      </select>
      <button class="mail-chip" :class="{ on: settingsOpen }" title="邮箱配置" @click="toggleSettings">
        ⚙
      </button>
      <button
        class="mail-chip"
        :disabled="store.syncing || !configured"
        :title="configured ? '立即收取一次' : '先完成配置'"
        @click="sync"
      >
        {{ store.syncing ? '收取中…' : '⟳' }}
      </button>
    </div>

    <!-- 配置面板 -->
    <div v-if="settingsOpen" class="mail-settings">
      <div class="mail-field">
        <label>邮箱地址</label>
        <input v-model="form.address" class="mail-search" placeholder="例如 123456789@qq.com" />
      </div>
      <div class="mail-field">
        <label>IMAP 授权码</label>
        <input
          v-model="form.authCode"
          class="mail-search"
          type="password"
          :placeholder="settings?.authCode ? '已保存（留空不修改）' : '16 位授权码'"
        />
      </div>

      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.enabled" type="checkbox" />
          <span>每 15 分钟自动收取</span>
        </label>
      </div>
      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.autoTodo" type="checkbox" />
          <span>高置信度邮件自动建待办</span>
        </label>
      </div>
      <div class="mail-field">
        <label class="mail-check">
          <input v-model="form.llmJudge" type="checkbox" />
          <span>用 AI 精判模糊邮件（需配置 API Key）</span>
        </label>
      </div>

      <div class="mail-field">
        <label>永不重要</label>
        <input v-model="form.ignoreSenders" class="mail-search" placeholder="域名或地址片段，逗号分隔" />
      </div>
      <div class="mail-field">
        <label>重点关注词</label>
        <input v-model="form.extraKeywords" class="mail-search" placeholder="命中即抬为重要，如：三方、政审" />
      </div>

      <details class="mail-advanced">
        <summary>高级：服务器与范围</summary>
        <div class="mail-field">
          <label>IMAP 服务器</label>
          <input v-model="form.imapHost" class="mail-search" />
        </div>
        <div class="mail-field">
          <label>端口 / SSL</label>
          <input v-model.number="form.imapPort" class="mail-port" type="number" />
          <label class="mail-check inline">
            <input v-model="form.ssl" type="checkbox" />
            <span>SSL</span>
          </label>
        </div>
        <div class="mail-field">
          <label>文件夹 / 每次读取</label>
          <input v-model="form.folder" class="mail-port" />
          <input v-model.number="form.fetchLimit" class="mail-port" type="number" min="5" max="200" />
        </div>
      </details>

      <div class="mail-actions">
        <button class="btn" :disabled="probing" @click="probe">
          {{ probing ? '测试中…' : '测试连接' }}
        </button>
        <button class="btn primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button class="btn" @click="settingsOpen = false">取消</button>
      </div>

      <div v-if="settings?.lastError" class="mail-error">上次失败：{{ settings.lastError }}</div>
    </div>

    <!-- 列表 -->
    <div class="mail-list">
      <article
        v-for="m in store.items"
        :key="m.id"
        class="mail-card"
        :class="[m.importantLevel, { unread: !m.read }]"
        @click="openDetail(m)"
      >
        <div class="mail-row-1">
          <span class="mail-level" :class="m.importantLevel" :title="m.scoreReason || ''">
            {{ levelLabel(m.importantLevel) }}
          </span>
          <span class="mail-sender" :title="m.senderAddr">{{ m.senderName }}</span>
          <span class="mail-time">{{ fmtTime(m.receivedAt) }}</span>
        </div>
        <div class="mail-subject" :title="m.subject">{{ m.subject }}</div>
        <div v-if="m.snippet" class="mail-snippet">{{ m.snippet }}</div>

        <div class="mail-row-2">
          <span v-if="m.hasAttachment" class="mail-flag" title="含附件">📎</span>
          <span v-if="m.verdictBy === 'llm'" class="mail-flag ai" title="AI 参与判定">AI</span>
          <span class="mail-score" :title="m.scoreReason || ''">{{ m.importantScore }} 分</span>
          <span v-if="m.todoExists" class="mail-todo-tag" :title="m.todoContent || ''">
            ✓ 待办{{ m.todoDueDate ? ' · ' + shortDate(m.todoDueDate) : '' }}
          </span>
          <span class="mail-spacer"></span>
          <button v-if="!m.todoExists" class="mail-act" @click.stop="toTodo(m)">＋ 待办</button>
          <button class="mail-act ghost" title="不再显示这封" @click.stop="ignore(m)">忽略</button>
        </div>
      </article>

      <div v-if="store.items.length === 0 && !store.loading" class="mail-empty muted">
        <template v-if="!configured">填好上面的配置，就能开始收信了。</template>
        <template v-else-if="scope === 'todo'">还没有邮件转成待办。</template>
        <template v-else>
          暂时没有重要邮件。点 <b>⟳</b> 立即收一次，或等下次自动收取。
        </template>
      </div>
    </div>

    <footer class="mail-foot">
      <span v-if="store.stats?.lastSyncAt" class="muted">上次收取 {{ store.stats.lastSyncAt }}</span>
      <span v-else class="muted">尚未收取过</span>
      <span v-if="store.stats?.lastError" class="mail-foot-error" :title="store.stats.lastError">
        收取失败
      </span>
    </footer>

    <!-- 详情 -->
    <div v-if="detail" class="mail-detail-mask" @click.self="detail = null">
      <div class="mail-detail">
        <header class="mail-detail-head">
          <div class="mail-detail-subject">{{ detail.subject }}</div>
          <button class="mail-detail-close" @click="detail = null">×</button>
        </header>
        <div class="mail-detail-meta">
          <span>{{ detail.senderName }} &lt;{{ detail.senderAddr }}&gt;</span>
          <span>{{ fmtFull(detail.receivedAt) }}</span>
          <span class="mail-level" :class="detail.importantLevel">
            {{ levelLabel(detail.importantLevel) }} · {{ detail.importantScore }} 分
          </span>
        </div>
        <div v-if="detail.scoreReason" class="mail-detail-reason">判定依据：{{ detail.scoreReason }}</div>
        <pre class="mail-detail-body">{{ detail.body || detail.snippet || '（无正文）' }}</pre>
        <footer class="mail-detail-foot">
          <span v-if="detail.todoExists" class="mail-todo-tag">✓ 已转待办：{{ detail.todoContent }}</span>
          <button v-else class="btn primary" @click="toTodo(detail)">＋ 加入待办</button>
          <span class="mail-spacer"></span>
          <button class="btn" @click="remove(detail)">删除记录</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMailStore } from '@/stores/mail'
import { fetchMailDetail } from '@/api/mail'
import { useLiveReload } from '@/composables/useLiveReload'
import { friendlyError, toastError, toastSuccess } from '@/utils/toast'
import type { MailMessageView, MailSettings } from '@/types'

const store = useMailStore()

const keyword = ref('')
const scope = ref<'important' | 'all' | 'todo'>('important')
const settingsOpen = ref(false)
const detail = ref<MailMessageView | null>(null)
const probing = ref(false)
const saving = ref(false)

const settings = computed(() => store.settings)
const configured = computed(() => !!store.settings?.configured)

/** 配置表单：用本地副本编辑，保存成功后才回写 store，避免边输边污染状态 */
const form = reactive<MailSettings>({
  enabled: true,
  address: '',
  authCode: '',
  imapHost: 'imap.qq.com',
  imapPort: 993,
  ssl: true,
  folder: 'INBOX',
  autoTodo: true,
  llmJudge: true,
  ignoreSenders: '',
  extraKeywords: '',
  fetchLimit: 30,
  lastSyncAt: '',
  configured: false,
  lastError: ''
})

let searchTimer: number | undefined
watch(keyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(reload, 300)
})
watch(scope, () => reload())

onMounted(async () => {
  try {
    const s = await store.loadSettings()
    fillForm(s)
  } catch (e) {
    console.warn('[mail] 配置加载失败', e)
  }
  reload()
})

// 定时收取、AI 工具转待办都会改这批数据
useLiveReload('mail', () => reload())

function reload() {
  store
    .load({
      scope: scope.value,
      keyword: keyword.value.trim() || undefined,
      limit: 100
    })
    .catch((e) => console.warn('[mail] 加载失败', e))
}

function fillForm(s: MailSettings) {
  form.enabled = s.enabled
  form.address = s.address
  // 授权码是打码值，原样放进去；后端遇到 **** 开头的值会视为「不修改」
  form.authCode = s.authCode
  form.imapHost = s.imapHost
  form.imapPort = s.imapPort
  form.ssl = s.ssl
  form.folder = s.folder
  form.autoTodo = s.autoTodo
  form.llmJudge = s.llmJudge
  form.ignoreSenders = s.ignoreSenders
  form.extraKeywords = s.extraKeywords
  form.fetchLimit = s.fetchLimit
}

function toggleSettings() {
  settingsOpen.value = !settingsOpen.value
  if (settingsOpen.value && store.settings) {
    fillForm(store.settings)
  }
}

async function save() {
  if (!form.address.trim()) {
    toastError('请填写邮箱地址')
    return
  }
  saving.value = true
  try {
    const saved = await store.saveSettings({ ...form })
    fillForm(saved)
    toastSuccess(saved.configured ? '配置已保存，点 ⟳ 可以开始收信' : '已保存，还需要填写授权码')
    if (saved.configured) {
      settingsOpen.value = false
      reload()
    }
  } catch (e) {
    toastError('保存失败：' + friendlyError(e))
  } finally {
    saving.value = false
  }
}

async function probe() {
  probing.value = true
  try {
    const r = await store.probe()
    if (r.ok) {
      toastSuccess(r.message)
    } else {
      toastError(r.message || '连接失败')
    }
    // 失败原因要落到界面上，不能只在 toast 里飘一下
    await store.loadSettings().then(fillForm).catch(() => {})
  } catch (e) {
    toastError('测试连接失败：' + friendlyError(e))
  } finally {
    probing.value = false
  }
}

async function sync() {
  try {
    const r = await store.sync()
    if (!r.ok) {
      toastError(r.message || '收取失败')
    } else {
      toastSuccess(r.message)
    }
    reload()
    store.loadSettings().then(fillForm).catch(() => {})
  } catch (e) {
    toastError('收取失败：' + friendlyError(e))
  }
}

async function openDetail(m: MailMessageView) {
  // 列表接口不带正文，先用手上的摘要占位，再异步补全，点开就有反馈
  detail.value = m
  try {
    const full = await fetchMailDetail(m.id)
    // 用户可能在请求期间关掉了弹层或翻到了另一封
    if (detail.value?.id === m.id) detail.value = full
  } catch (e) {
    toastError('打开邮件失败：' + friendlyError(e))
  }
}

async function toTodo(m: MailMessageView) {
  try {
    const updated = await store.toTodo(m.id)
    toastSuccess('已加入待办：' + (updated.todoContent || m.subject))
    if (detail.value && detail.value.id === m.id) {
      detail.value = { ...detail.value, ...updated }
    }
  } catch (e) {
    toastError('加入待办失败：' + friendlyError(e))
  }
}

async function ignore(m: MailMessageView) {
  try {
    await store.ignore(m.id)
    if (detail.value?.id === m.id) detail.value = null
  } catch (e) {
    toastError('操作失败：' + friendlyError(e))
  }
}

async function remove(m: MailMessageView) {
  try {
    await store.remove(m.id)
    if (detail.value?.id === m.id) detail.value = null
    toastSuccess('已删除这条邮件记录')
  } catch (e) {
    toastError('删除失败：' + friendlyError(e))
  }
}

function levelLabel(level: string) {
  if (level === 'high') return '重要'
  if (level === 'medium') return '可关注'
  return '普通'
}

/** 今天的邮件只显示时刻，更早的显示日期 —— 列表里最常见的是今天 */
function fmtTime(s: string) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function fmtFull(s: string) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 2026-11-02 → 11/2 */
function shortDate(s: string) {
  const parts = s.split('-')
  return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : s
}
</script>
