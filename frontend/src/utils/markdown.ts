import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

// 配置 marked：代码块用 highlight.js 做语法高亮
const marked = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      const language = hljs.getLanguage(lang) ? lang : 'plaintext'
      return hljs.highlight(code, { language }).value
    }
  })
)

/**
 * 将 Markdown 文本渲染为安全的 HTML（含代码块语法高亮）。
 *
 * 内容可能来自 LLM（不可信输入），渲染前必须用 DOMPurify 做 XSS 消毒，
 * 避免注入脚本或恶意标签。
 */
export function renderMarkdown(md: string): string {
  const html = marked.parse(md, { async: false }) as string
  return DOMPurify.sanitize(html)
}
