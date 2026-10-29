import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 将 Markdown 文本渲染为安全的 HTML。
 *
 * 总结内容可能来自 LLM（不可信输入），渲染前必须用 DOMPurify 做 XSS 消毒，
 * 避免注入脚本或恶意标签。
 */
export function renderMarkdown(md: string): string {
  const html = marked.parse(md, { async: false }) as string
  return DOMPurify.sanitize(html)
}
