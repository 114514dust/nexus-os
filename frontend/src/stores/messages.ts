import { defineStore } from 'pinia'
import {
  fetchMessages,
  markMessageRead,
  markAllMessagesRead,
  deleteMessage
} from '@/api/messages'
import type { Message, MessageSourceStat } from '@/types'

/**
 * 消息流状态。
 *
 * 关键点是「筛选条件放在 store 里」：切换来源、只看未读之后，
 * 定时刷新和 LiveReload 自动重载都会沿用它，不会把用户的选择冲掉。
 */
export const useMessageStore = defineStore('messages', {
  state: () => ({
    items: [] as Message[],
    total: 0,
    /** 全部未读数（不受筛选影响） */
    unread: 0,
    sources: [] as MessageSourceStat[],
    loading: false,
    loadingMore: false,
    /** 当前来源筛选，空串表示全部 */
    source: '',
    unreadOnly: false,
    size: 30
  }),
  getters: {
    hasMore: (s) => s.items.length < s.total
  },
  actions: {
    async load() {
      this.loading = true
      try {
        const result = await fetchMessages({
          page: 0,
          size: this.size,
          source: this.source || undefined,
          unread: this.unreadOnly || undefined
        })
        this.items = result.list
        this.total = result.total
        this.unread = result.unread
        this.sources = result.sources
      } finally {
        this.loading = false
      }
    },
    async loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      try {
        const page = Math.floor(this.items.length / this.size)
        const result = await fetchMessages({
          page,
          size: this.size,
          source: this.source || undefined,
          unread: this.unreadOnly || undefined
        })
        // 抓取是并发的，翻页间隙可能有新消息插进来，按 id 去重再拼接
        const seen = new Set(this.items.map((m) => m.id))
        this.items = this.items.concat(result.list.filter((m) => !seen.has(m.id)))
        this.total = result.total
        this.unread = result.unread
        this.sources = result.sources
      } finally {
        this.loadingMore = false
      }
    },
    async setSource(key: string) {
      if (this.source === key) return
      this.source = key
      await this.load()
    },
    async setUnreadOnly(value: boolean) {
      if (this.unreadOnly === value) return
      this.unreadOnly = value
      await this.load()
    },
    /** 本地先改再请求：点开就变灰，不用等一次往返 */
    async markRead(m: Message, read = true) {
      if (m.read === read) return
      m.read = read
      this.bumpUnread(m.source, read ? -1 : 1)
      try {
        await markMessageRead(m.id, read)
      } catch (e) {
        m.read = !read
        this.bumpUnread(m.source, read ? 1 : -1)
        throw e
      }
    },
    async markAllRead() {
      const n = await markAllMessagesRead(this.source || undefined)
      await this.load()
      return n
    },
    async remove(m: Message) {
      await deleteMessage(m.id)
      this.items = this.items.filter((x) => x.id !== m.id)
      this.total = Math.max(0, this.total - 1)
      if (!m.read) this.bumpUnread(m.source, -1)
    },
    /** 未读数在本地做增量，避免为了一个数字再打一次列表请求 */
    bumpUnread(source: string, delta: number) {
      this.unread = Math.max(0, this.unread + delta)
      const stat = this.sources.find((s) => s.key === source)
      if (stat) stat.unread = Math.max(0, stat.unread + delta)
    }
  }
})
