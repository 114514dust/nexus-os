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

import datetime
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
    description = "列出当前所有待办事项（含到期日与是否逾期）"
    params = "{}"

    def call(self, params, client):
        items = client.get("/api/todos")
        lines = []
        for it in items[:20]:
            mark = "✅已完成" if it.get("done") else "⬜未完成"
            due = it.get("dueDate")
            due_txt = f"（到期 {due}{'，已逾期' if not it.get('done') and _is_overdue(due) else ''}）" if due else ""
            lines.append(f"#{it.get('id')} [{mark}] {it.get('content', '')}{due_txt}")
        out = "\n".join(lines) if lines else "（当前没有待办）"
        return "待办列表：\n" + out + _tail(items)


def _is_overdue(due: str) -> bool:
    """dueDate 是 yyyy-MM-dd，只比较日期。"""
    try:
        return due < datetime.date.today().isoformat()
    except Exception:
        return False


class TodoAddTool(Tool):
    name = "todo_add"
    description = "新增一条待办事项；dueDate 可选（yyyy-MM-dd），填了就参与到期提醒"
    params = '{"content": "待办内容", "dueDate": "2026-11-02"}'

    def call(self, params, client):
        content = str(params.get("content", "")).strip()
        if not content:
            return "错误：缺少待办内容 content"
        body = {"content": content}
        due = params.get("dueDate")
        if due:
            body["dueDate"] = str(due).strip()
        created = client.post("/api/todos", body)
        suffix = f"，到期日 {created['dueDate']}" if created.get("dueDate") else ""
        return f"已添加待办 #{created['id']}：「{created['content']}」{suffix}"


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


# ---- 求职追踪 ----
_JOB_STAGE_LABEL = {
    "wish": "想投",
    "applied": "已投递",
    "written": "笔试",
    "interview1": "一面",
    "interview2": "二面",
    "hr": "HR 面",
    "offer": "Offer",
    "accepted": "已入职",
    "rejected": "未通过",
}

_JOB_CAT_LABEL = {
    "autumn": "秋招",
    "spring": "春招",
    "intern": "实习",
    "social": "社招",
    "soe": "国企",
    "other": "其他",
}


def _job_line(it: dict) -> str:
    """一行描述一条投递：公司 · 岗位 [类型] —— 进度（下一步 / 日期）"""
    cat = _JOB_CAT_LABEL.get(it.get("category"), it.get("category", ""))
    stage = _JOB_STAGE_LABEL.get(it.get("stage"), it.get("stage", ""))
    head = f"#{it.get('id')} {it.get('company', '')}"
    if it.get("jobTitle"):
        head += f" · {it['jobTitle']}"
    line = f"{head} [{cat}] —— {stage}"
    extra = []
    if it.get("nextAt"):
        extra.append(f"{it['nextAt']}")
    if it.get("nextAction"):
        extra.append(str(it["nextAction"]))
    if extra:
        line += f"（{' / '.join(extra)}）"
    return line


class JobListTool(Tool):
    name = "job_list"
    description = "查看求职投递记录与面试进度（可按类型或范围筛选）"
    params = '{"category": "autumn|spring|intern|social|soe|other", "scope": "active|closed|all"}'

    def call(self, params, client):
        query = {}
        cat = str(params.get("category", "")).strip()
        if cat in _JOB_CAT_LABEL:
            query["category"] = cat
        scope = str(params.get("scope", "")).strip()
        if scope in ("active", "closed", "all"):
            query["scope"] = scope
        query.setdefault("scope", "all")
        query["sort"] = "due"
        items = client.get("/api/jobs", **query)
        lines = [_job_line(it) for it in items[:20]]
        out = "\n".join(lines) if lines else "（还没有投递记录）"
        return "求职投递记录：\n" + out + _tail(items)


class JobAddTool(Tool):
    name = "job_add"
    description = "新增一条求职投递记录（公司必填，其余可选）"
    params = (
        '{"company": "公司名", "jobTitle": "岗位", '
        '"category": "autumn|spring|intern|social|soe|other", '
        '"url": "https://...", "nextAction": "下一步", "nextAt": "yyyy-MM-dd"}'
    )

    def call(self, params, client):
        company = str(params.get("company", "")).strip()
        if not company:
            return "错误：缺少公司名称 company"
        body = {"company": company}
        for key in ("jobTitle", "url", "nextAction"):
            v = str(params.get(key, "") or "").strip()
            if v:
                body[key] = v
        cat = str(params.get("category", "")).strip()
        if cat in _JOB_CAT_LABEL:
            body["category"] = cat
        next_at = str(params.get("nextAt", "") or "").strip()
        if next_at:
            body["nextAt"] = next_at
        created = client.post("/api/jobs", body)
        return f"已记录投递：{_job_line(created)}"


class JobUpdateStageTool(Tool):
    name = "job_update_stage"
    description = "更新某条投递的进度阶段（可用 id，或用公司名定位）"
    params = (
        '{"id": 1, "company": "公司名", '
        '"stage": "applied|written|interview1|interview2|hr|offer|accepted|rejected", "note": "备注"}'
    )

    def call(self, params, client):
        stage = str(params.get("stage", "")).strip()
        if stage not in _JOB_STAGE_LABEL:
            return "错误：stage 需为 " + " / ".join(_JOB_STAGE_LABEL.keys())

        job_id = params.get("id")
        if job_id is None:
            company = str(params.get("company", "")).strip()
            if not company:
                return "错误：需要提供 id 或 company 来定位投递记录"
            items = client.get("/api/jobs", scope="all")
            matched = [it for it in items if company in str(it.get("company", ""))]
            if not matched:
                return f"没有找到公司包含「{company}」的投递记录"
            if len(matched) > 1:
                lines = [_job_line(it) for it in matched]
                return "匹配到多条记录，请用 id 指定：\n" + "\n".join(lines)
            job_id = matched[0]["id"]

        body = {"stage": stage}
        note = str(params.get("note", "") or "").strip()
        if note:
            body["stageNote"] = note
        updated = client.put(f"/api/jobs/{int(job_id)}", body)
        return f"已更新：{_job_line(updated)}"


class JobStatsTool(Tool):
    name = "job_stats"
    description = "查看求职概览统计（投递数、面试中、Offer、回复率）"
    params = "{}"

    def call(self, params, client):
        s = client.get("/api/jobs/stats")
        return (
            f"求职概览：共投递 {s.get('total', 0)} 家，进行中 {s.get('active', 0)} 家，"
            f"面试中 {s.get('interviewing', 0)} 家，Offer {s.get('offers', 0)} 个，"
            f"未通过 {s.get('rejected', 0)} 家，回复率 {s.get('replyRate', 0)}%；"
            f"近 7 天有 {s.get('upcoming', 0)} 项安排。"
        )


# ---- 校招岗位 ----
def _posting_line(it: dict) -> str:
    """一行描述一个岗位：公司 · 岗位方向 [性质/批次]（城市 / 截止）"""
    head = f"#{it.get('id')} {it.get('company', '')}"
    pos = str(it.get("positions") or "").strip()
    if pos:
        head += " · " + (pos if len(pos) <= 60 else pos[:60] + "…")
    tags = [t for t in (it.get("companyType"), it.get("batch")) if t]
    line = head + (f" [{'/'.join(tags)}]" if tags else "")
    extra = []
    if it.get("city"):
        city = str(it["city"])
        extra.append(city if len(city) <= 20 else city[:20] + "…")
    if it.get("deadlineText"):
        extra.append("截止" + str(it["deadlineText"]))
    if extra:
        line += f"（{' / '.join(extra)}）"
    if it.get("matched"):
        line += " ★命中:" + "/".join(it.get("matchKeywords") or [])
    return line


class PostingListTool(Tool):
    name = "posting_list"
    description = "查看最近在招的校招岗位（来自秋招网抓取 + 手动添加，可按关键词/批次/状态筛选）"
    params = (
        '{"keyword": "公司或岗位关键词", "category": "autumn|spring|intern|social|soe|other", '
        '"scope": "pending|applied|all", "onlyMatched": true}'
    )

    def call(self, params, client):
        query = {}
        kw = str(params.get("keyword", "") or "").strip()
        if kw:
            query["keyword"] = kw
        cat = str(params.get("category", "") or "").strip()
        if cat in _JOB_CAT_LABEL:
            query["category"] = cat
        scope = str(params.get("scope", "") or "").strip()
        query["scope"] = scope if scope in ("pending", "applied", "all") else "pending"
        if params.get("onlyMatched") in (True, "true", 1, "1"):
            query["onlyMatched"] = True

        items = client.get("/api/postings", **query)
        stats = client.get("/api/postings/stats")
        lines = [_posting_line(it) for it in items[:15]]
        if not lines:
            hint = "（没有符合条件的岗位。可以让我抓一次最新岗位，或者你自己在组件里点刷新）"
            return (
                f"校招岗位：待处理 {stats.get('pending', 0)} 个，已加入追踪 {stats.get('applied', 0)} 个。\n"
                + hint
            )
        out = "\n".join(lines)
        tail = "" if len(items) <= 15 else f"\n（共 {len(items)} 个，仅列前 15 个）"
        return (
            f"校招岗位（待处理 {stats.get('pending', 0)} / 已加入 {stats.get('applied', 0)}）：\n"
            + out + tail
            + "\n\n想投哪个直接说「把 XX 加入求职追踪」即可。"
        )


class PostingApplyTool(Tool):
    name = "posting_apply"
    description = "把某个校招岗位加入求职追踪（生成一条投递记录），可用岗位 id 或公司名定位"
    params = '{"id": 1, "company": "公司名", "jobTitle": "投的岗位（可选）"}'

    def call(self, params, client):
        posting_id = params.get("id")
        target = None
        if posting_id is None:
            company = str(params.get("company", "") or "").strip()
            if not company:
                return "错误：需要提供 id 或 company 来定位岗位"
            items = client.get("/api/postings", scope="all")
            matched = [
                it for it in items
                if company in str(it.get("company", ""))
                or company in str(it.get("positions", ""))
            ]
            if not matched:
                return f"没有找到公司或岗位包含「{company}」的校招岗位，可能还没抓取到"
            if len(matched) > 1:
                lines = [_posting_line(it) for it in matched]
                return "匹配到多个岗位，请用 id 指定：\n" + "\n".join(lines)
            target = matched[0]
            posting_id = target["id"]
            if target.get("jobId"):
                return f"「{target.get('company')}」已经在求职追踪里了（投递 #{target['jobId']}），不用重复添加"

        body = {}
        title = str(params.get("jobTitle", "") or "").strip()
        if title:
            body["jobTitle"] = title
        result = client.post(f"/api/postings/{int(posting_id)}/apply", body)
        job = result.get("job") or {}
        if not result.get("created"):
            return f"这条岗位之前已经加入过：{_job_line(job)}"
        return f"已加入求职追踪：{_job_line(job)}"


class PostingRefreshTool(Tool):
    name = "posting_refresh"
    description = "立即从秋招网抓取一次最新的校招岗位（约 30 秒返回）"
    params = "{}"

    def call(self, params, client):
        r = client.post("/api/postings/refresh")
        if not r.get("ok"):
            return f"抓取失败：{r.get('message', '源站无响应')}"
        return (
            f"抓取完成：拉取 {r.get('fetched', 0)} 条，新增 {r.get('added', 0)} 个，"
            f"更新 {r.get('updated', 0)} 个。"
        )


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
    description = "综合查看工作台全貌（待办 + 便签 + 链接 + 订阅 + 求职进度 + 在招岗位）"
    params = "{}"

    def call(self, params, client):
        todos = client.get("/api/todos")
        notes = client.get("/api/notes")
        links = client.get("/api/links")
        subs = client.get("/api/subscriptions")
        jobs = client.get("/api/jobs", scope="active", sort="due")
        job_stats = client.get("/api/jobs/stats")
        postings = client.get("/api/postings", scope="pending")
        posting_stats = client.get("/api/postings/stats")

        todo_lines = [f"#{it.get('id')} {'[完成]' if it.get('done') else '[未完成]'} {it.get('content','')}" for it in todos[:20]]
        note_lines = [f"#{it.get('id')} {it.get('content','')}" for it in notes[:20]]
        link_lines = [f"#{it.get('id')} {it.get('title','')} → {it.get('url','')}" for it in links[:20]]
        sub_lines = [f"#{it.get('id')} [{'B站' if it.get('type')=='bilibili' else 'RSS'}] {it.get('name','')}" for it in subs[:20]]
        job_lines = [_job_line(it) for it in jobs[:10]]
        job_head = (
            f"（共 {job_stats.get('total', 0)} 家，面试中 {job_stats.get('interviewing', 0)}，"
            f"Offer {job_stats.get('offers', 0)}，回复率 {job_stats.get('replyRate', 0)}%）"
        )
        posting_lines = [_posting_line(it) for it in postings[:5]]

        parts = [
            "【待办】" + ("\n" + "\n".join(todo_lines) if todo_lines else "（无）"),
            "【便签】" + ("\n" + "\n".join(note_lines) if note_lines else "（无）"),
            "【链接】" + ("\n" + "\n".join(link_lines) if link_lines else "（无）"),
            "【订阅】" + ("\n" + "\n".join(sub_lines) if sub_lines else "（无）"),
            "【求职】" + job_head + ("\n" + "\n".join(job_lines) if job_lines else "\n（暂无进行中的投递）"),
            "【校招岗位】待处理 " + str(posting_stats.get("pending", 0))
            + " / 已加入 " + str(posting_stats.get("applied", 0))
            + ("\n" + "\n".join(posting_lines) if posting_lines else "\n（暂无待处理岗位）"),
        ]
        return "\n\n".join(parts)


# 工具注册表：classify 节点从这里生成工具描述清单，dispatch 节点查表执行
_TOOL_CLASSES: List[type] = [
    TodoListTool, TodoAddTool, TodoDoneTool, TodoDeleteTool,
    NoteListTool, NoteAddTool,
    LinkListTool, LinkAddTool,
    AlgorithmSearchTool,
    JobListTool, JobAddTool, JobUpdateStageTool, JobStatsTool,
    PostingListTool, PostingApplyTool, PostingRefreshTool,
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
