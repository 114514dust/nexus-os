"""工具层：让 AI 操控工作台组件的可插拔工具集。

设计意图（呼应设计文档「MCP 把接入层与 Agent 工具层统一」）：
- 每个工具 = name + description + params + call(params)，结构等同 function-calling 的 tool 定义；
- classify 节点把工具描述清单喂给 LLM 做意图识别，dispatch 节点查表执行；
- **新增「操控某组件」能力 = 新增一个 Tool 子类 + 注册进 TOOLS，不改编排图**。

Java 是数据所有者，本层通过 httpx 反向调用 Java 的 REST API（/api/todos 等），
Python 自身无状态、不落库——与每日总结链路的职责边界保持一致。

工具结果的文本格式刻意做「语义化」（带状态图标 / 字段含义），而不是裸 JSON，
因为结果会直接喂给 LLM 转述给用户，格式越清晰，转述越准确。
"""
from __future__ import annotations

from typing import Any, Dict, List

import httpx

import config


class NexusClient:
    """Java 后端 REST API 的轻量客户端（仅供工具层调用）。"""

    def __init__(self, base_url: str = ""):
        self.base_url = (base_url or config.BACKEND_BASE_URL).rstrip("/")

    def _request(self, method: str, path: str, **kwargs) -> Dict[str, Any]:
        url = self.base_url + path
        with httpx.Client(timeout=30) as client:
            resp = client.request(method, url, **kwargs)
            resp.raise_for_status()
            body = resp.json()
        if body.get("code") != 0:
            raise RuntimeError(f"后端返回错误[{body.get('code')}]: {body.get('message')}")
        return body.get("data")

    def get(self, path: str, **params) -> Any:
        return self._request("GET", path, params=params)

    def post(self, path: str, json_body: dict = None) -> Any:
        return self._request("POST", path, json=json_body or {})

    def put(self, path: str, json_body: dict = None) -> Any:
        return self._request("PUT", path, json=json_body or {})

    def delete(self, path: str) -> Any:
        return self._request("DELETE", path)


class Tool:
    """工具基类。子类只需实现 call() 并声明元信息。"""

    name: str = ""
    description: str = ""
    params: str = ""

    def call(self, params: Dict[str, Any], client: NexusClient) -> str:
        raise NotImplementedError


def _tail(items: list, limit: int = 20) -> str:
    """列表结果的统一尾注：超过 limit 时提示总数。"""
    return f"\n…（共 {len(items)} 条，仅展示前 {limit} 条）" if len(items) > limit else ""


# ---- 待办 ----
class TodoListTool(Tool):
    name = "todo_list"
    description = "列出当前所有待办事项"
    params = "{}"

    def call(self, params, client):
        items = client.get("/api/todos")
        lines = []
        for it in items[:20]:
            mark = "✅已完成" if it.get("done") else "⬜未完成"
            lines.append(f"#{it.get('id')} [{mark}] {it.get('content', '')}")
        out = "\n".join(lines) if lines else "（当前没有待办）"
        return "待办列表：\n" + out + _tail(items)


class TodoAddTool(Tool):
    name = "todo_add"
    description = "新增一条待办事项"
    params = '{"content": "待办内容"}'

    def call(self, params, client):
        content = str(params.get("content", "")).strip()
        if not content:
            return "错误：缺少待办内容 content"
        created = client.post("/api/todos", {"content": content})
        return f"已添加待办 #{created['id']}：「{created['content']}」"


class TodoDoneTool(Tool):
    name = "todo_done"
    description = "把某条待办标记为已完成（需要 id）"
    params = '{"id": 1}'

    def call(self, params, client):
        tid = params.get("id")
        if tid is None:
            return "错误：缺少待办 id"
        updated = client.put(f"/api/todos/{int(tid)}", {"done": True})
        return f"已把待办 #{updated['id']}「{updated['content']}」标记为已完成"


class TodoDeleteTool(Tool):
    name = "todo_delete"
    description = "删除某条待办（需要 id）"
    params = '{"id": 1}'

    def call(self, params, client):
        tid = params.get("id")
        if tid is None:
            return "错误：缺少待办 id"
        client.delete(f"/api/todos/{int(tid)}")
        return f"已删除待办 #{int(tid)}"


# ---- 便签 ----
class NoteListTool(Tool):
    name = "note_list"
    description = "列出当前所有便签"
    params = "{}"

    def call(self, params, client):
        items = client.get("/api/notes")
        lines = [f"#{it.get('id')} {it.get('content', '')}" for it in items[:20]]
        out = "\n".join(lines) if lines else "（当前没有便签）"
        return "便签列表：\n" + out


class NoteAddTool(Tool):
    name = "note_add"
    description = "新增一条便签"
    params = '{"content": "便签内容"}'

    def call(self, params, client):
        content = str(params.get("content", "")).strip()
        if not content:
            return "错误：缺少便签内容 content"
        created = client.post("/api/notes", {"content": content})
        return f"已添加便签 #{created['id']}：「{created['content']}」"


# ---- 快捷链接 ----
class LinkListTool(Tool):
    name = "link_list"
    description = "列出当前所有快捷链接"
    params = "{}"

    def call(self, params, client):
        items = client.get("/api/links")
        lines = [f"#{it.get('id')} {it.get('title', '')} → {it.get('url', '')}" for it in items[:20]]
        out = "\n".join(lines) if lines else "（当前没有快捷链接）"
        return "快捷链接列表：\n" + out


class LinkAddTool(Tool):
    name = "link_add"
    description = "新增一个快捷链接"
    params = '{"title": "链接名称", "url": "https://..."}'

    def call(self, params, client):
        title = str(params.get("title", "")).strip()
        url = str(params.get("url", "")).strip()
        if not title or not url:
            return "错误：缺少链接 title 或 url"
        created = client.post("/api/links", {"title": title, "url": url})
        return f"已添加链接 #{created['id']}：「{created['title']}」→ {created['url']}"


# ---- 高频算法题 ----
_DIFF_LABEL = {"easy": "简单", "medium": "中等", "hard": "困难"}


class AlgorithmSearchTool(Tool):
    name = "algorithm_search"
    description = "搜索高频算法题（可选关键词/难度）"
    params = '{"keyword": "关键词", "difficulty": "easy|medium|hard"}'

    def call(self, params, client):
        query = {}
        kw = str(params.get("keyword", "")).strip()
        diff = str(params.get("difficulty", "")).strip()
        if kw:
            query["keyword"] = kw
        if diff in ("easy", "medium", "hard"):
            query["difficulty"] = diff
        items = client.get("/api/algorithms", **query)
        lines = []
        for it in items[:20]:
            d = _DIFF_LABEL.get(it.get("difficulty"), it.get("difficulty", ""))
            star = it.get("star", 0) or 0
            lines.append(f"#{it.get('id')} [{d}] {it.get('title', '')} 🔥{it.get('frequency', 0)} ★{star}")
        out = "\n".join(lines) if lines else "（没有匹配的算法题）"
        return "算法题：\n" + out


# ---- 订阅 ----
class SubscriptionListTool(Tool):
    name = "subscription_list"
    description = "列出当前所有订阅源（RSS / B站）"
    params = "{}"

    def call(self, params, client):
        items = client.get("/api/subscriptions")
        lines = []
        for it in items[:20]:
            typ = "B站" if it.get("type") == "bilibili" else "RSS"
            state = "启用" if it.get("enabled") else "停用"
            lines.append(f"#{it.get('id')} [{typ}] {it.get('name', '')}（{state}）")
        out = "\n".join(lines) if lines else "（当前没有订阅源）"
        return "订阅源列表：\n" + out


# ---- 消息 ----
class MessageListTool(Tool):
    name = "message_list"
    description = "列出最近流入工作台的消息"
    params = "{}"

    def call(self, params, client):
        page = client.get("/api/messages", page=0, size=20)
        items = (page or {}).get("items", [])
        lines = []
        for it in items[:20]:
            src = it.get("source", "unknown")
            sender = str(it.get("sender") or "").strip()
            content = str(it.get("content") or "").strip()
            lines.append(f"[{src}] {sender}: {content}" if sender else f"[{src}] {content}")
        out = "\n".join(lines) if lines else "（当前没有消息）"
        return "最近消息：\n" + out


# ---- 每日总结 ----
class SummaryGenerateTool(Tool):
    name = "summary_generate"
    description = "生成今日每日总结"
    params = "{}"

    def call(self, params, client):
        created = client.post("/api/summaries/digest")
        return f"已生成每日总结（{created.get('summaryDate', '')}），内容：\n{str(created.get('content', ''))[:500]}"


# ---- 综合查询 ----
class QueryAllTool(Tool):
    name = "query_all"
    description = "综合查看工作台全貌（待办 + 便签 + 链接 + 订阅）"
    params = "{}"

    def call(self, params, client):
        todos = client.get("/api/todos")
        notes = client.get("/api/notes")
        links = client.get("/api/links")
        subs = client.get("/api/subscriptions")

        todo_lines = [f"#{it.get('id')} {'[完成]' if it.get('done') else '[未完成]'} {it.get('content','')}" for it in todos[:20]]
        note_lines = [f"#{it.get('id')} {it.get('content','')}" for it in notes[:20]]
        link_lines = [f"#{it.get('id')} {it.get('title','')} → {it.get('url','')}" for it in links[:20]]
        sub_lines = [f"#{it.get('id')} [{'B站' if it.get('type')=='bilibili' else 'RSS'}] {it.get('name','')}" for it in subs[:20]]

        parts = [
            "【待办】" + ("\n" + "\n".join(todo_lines) if todo_lines else "（无）"),
            "【便签】" + ("\n" + "\n".join(note_lines) if note_lines else "（无）"),
            "【链接】" + ("\n" + "\n".join(link_lines) if link_lines else "（无）"),
            "【订阅】" + ("\n" + "\n".join(sub_lines) if sub_lines else "（无）"),
        ]
        return "\n\n".join(parts)


# 工具注册表：classify 节点从这里生成工具描述清单，dispatch 节点查表执行
_TOOL_CLASSES: List[type] = [
    TodoListTool, TodoAddTool, TodoDoneTool, TodoDeleteTool,
    NoteListTool, NoteAddTool,
    LinkListTool, LinkAddTool,
    AlgorithmSearchTool,
    SubscriptionListTool,
    MessageListTool,
    SummaryGenerateTool,
    QueryAllTool,
]

TOOLS: Dict[str, Tool] = {cls.name: cls() for cls in _TOOL_CLASSES}


def tool_specs() -> str:
    """生成给 LLM 看的工具清单（名称 + 用途 + 参数）。"""
    lines = []
    for t in TOOLS.values():
        lines.append(f"- {t.name}: {t.description}；参数 {t.params}")
    return "\n".join(lines)


def dispatch(intent: str, params: Dict[str, Any]) -> str:
    """按意图执行工具，返回结果文本；意图未知时抛 KeyError（由调用方降级为闲聊）。"""
    tool = TOOLS[intent]
    return tool.call(params or {}, NexusClient())
