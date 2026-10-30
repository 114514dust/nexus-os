"""可插拔 LLM 客户端。

设计意图：编排图（graph.py）只依赖 `summarize()` 这一个抽象，不关心
"总结"到底由真实大模型完成，还是由规则式降级逻辑完成。这样：
- 无 API key 时链路依然可跑通、可演示；
- 换模型（OpenAI / DeepSeek / 通义）只改环境变量，不改图结构。

这就是"编排与 LLM 解耦"——也是 LangGraph 价值所在：图结构是资产，模型是可替换零件。
"""
from __future__ import annotations

import json
import time
from typing import Any, Dict, List, Optional

import httpx

import config


def _build_prompt(raw_text: str, date: str) -> str:
    return (
        f"你是一个个人工作台的每日总结助手。请基于 {date} 这一天流入工作台的原始信息，"
        f"生成一份简洁、有条理的中文每日总结。\n\n"
        f"要求：\n"
        f"1. 用 Markdown，包含「今日概览」「消息」「待办」「便签」「明日建议」几个部分；\n"
        f"2. 突出重要事项，忽略噪音；\n"
        f"3. 语气务实、可执行，不要空话套话。\n\n"
        f"原始信息如下：\n\n{raw_text}"
    )


def _call_openai_compatible(prompt: str, llm_config=None) -> str:
    """调用 OpenAI 兼容的 /chat/completions 接口。

    llm_config 为前端动态传入的配置（{apiKey, baseUrl, model}），优先于环境变量；
    缺失时回退到 config 的环境变量。
    """
    cfg = llm_config or {}
    base = (cfg.get("baseUrl") or config.LLM_BASE_URL).rstrip("/")
    url = base + "/chat/completions"
    api_key = cfg.get("apiKey") or config.LLM_API_KEY
    model = cfg.get("model") or config.LLM_MODEL

    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "你是个人工作台（Nexus）的每日总结助手，输出规范、简洁的中文 Markdown。",
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.3,
    }
    headers = {"Authorization": f"Bearer {api_key}"}
    with httpx.Client(timeout=90) as client:
        resp = client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    return data["choices"][0]["message"]["content"].strip()


def _rule_based_summary(raw_text: str, date: str) -> str:
    """规则式降级总结：不调大模型，基于结构化统计生成，保证链路可用。"""
    return (
        f"## {date} 每日总结\n\n"
        f"（当前未配置大模型，以下为规则式统计总结。在前端「设置」里配置 API Key 后即切换为智能总结。）\n\n"
        f"{raw_text}"
    )


def summarize(raw_text: str, date: str, llm_config=None) -> str:
    """对聚合后的原始信息生成总结。

    优先级：前端动态配置（llm_config.apiKey）> 环境变量 > 规则式降级。
    """
    if llm_config and llm_config.get("apiKey"):
        return _call_openai_compatible(_build_prompt(raw_text, date), llm_config)
    if config.llm_enabled():
        return _call_openai_compatible(_build_prompt(raw_text, date))
    return _rule_based_summary(raw_text, date)


# ---------------------------------------------------------------------------
# 对话（chat）与意图识别（classify）——供 Workflow 对话图使用
# ---------------------------------------------------------------------------

CHAT_SYSTEM = (
    "你是 Nexus 个人工作台的智能助手。你熟悉用户工作台里的各类信息（待办、便签、快捷链接、"
    "订阅源、消息、高频算法题、求职投递与面试进度、在招的校招岗位、每日总结），回答简洁、务实、用中文。"
)


def _call_chat(messages: List[Dict[str, str]], llm_config=None, temperature: float = 0.3) -> str:
    """调用 OpenAI 兼容的 /chat/completions 接口（多轮 messages 形式）。"""
    cfg = llm_config or {}
    base = (cfg.get("baseUrl") or config.LLM_BASE_URL).rstrip("/")
    url = base + "/chat/completions"
    api_key = cfg.get("apiKey") or config.LLM_API_KEY
    model = cfg.get("model") or config.LLM_MODEL

    payload = {"model": model, "messages": messages, "temperature": temperature}
    headers = {"Authorization": f"Bearer {api_key}"}
    with httpx.Client(timeout=90) as client:
        resp = client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    return data["choices"][0]["message"]["content"].strip()


def _llm_available(llm_config=None) -> bool:
    """是否走真实 LLM（动态配置优先，其次环境变量）。"""
    if llm_config and llm_config.get("apiKey"):
        return True
    return config.llm_enabled()



def classify(message: str, tool_specs: str, llm_config=None, history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
    """意图识别：把用户消息归为「某工具 + 参数」或「纯闲聊」。

    返回 {"intent": str, "params": dict}；纯闲聊时 intent == "chat"。
    历史对话会一并提供给 LLM，以解析「刚才那条待办」这类多轮指代；
    无 LLM 时走规则式关键词匹配降级，保证链路可跑通。
    """
    if _llm_available(llm_config):
        history_text = ""
        if history:
            turns = [f"{h.get('role', 'user')}: {h.get('content', '')}" for h in history]
            history_text = "最近对话历史：\n" + "\n".join(turns) + "\n\n"
        prompt = (
            f"你是工作台助手，负责识别用户意图并选择工具。\n\n"
            f"可用工具清单：\n{tool_specs}\n\n"
            f"{history_text}"
            f"用户当前消息：{message}\n\n"
            f"请只输出一个 JSON 对象，格式 {{\"intent\": \"工具名或chat\", \"params\": {{}}}}。\n"
            f"规则：若用户只是普通闲聊/提问，intent 用 \"chat\"；若要操作或查询工作台数据，"
            f"intent 用对应工具名，params 填工具所需参数。"
            f"注意结合历史理解指代（如「刚才那条待办」应解析出具体 id）；"
            f"若历史中找不到所需的 id 等参数，intent 用 \"chat\" 让助手追问，不要编造参数。"
            f"不要输出 JSON 以外的任何内容。"
        )
        try:
            raw = _call_chat(
                [{"role": "system", "content": CHAT_SYSTEM}, {"role": "user", "content": prompt}],
                llm_config,
                temperature=0.0,
            )
            # 容错：LLM 可能输出 ```json ... ``` 包裹，去掉代码围栏
            cleaned = raw.strip()
            if cleaned.startswith("```"):
                cleaned = cleaned.strip("`")
                if cleaned.startswith("json"):
                    cleaned = cleaned[4:]
            start, end = cleaned.find("{"), cleaned.rfind("}")
            if start >= 0 and end > start:
                data = json.loads(cleaned[start : end + 1])
                intent = str(data.get("intent", "chat")).strip()
                params = data.get("params") or {}
                if not isinstance(params, dict):
                    params = {}
                return {"intent": intent, "params": params}
        except Exception:
            pass  # LLM 解析失败 → 落到规则降级
    return _rule_based_classify(message, history)


def _rule_based_classify(message: str, history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
    """规则式意图识别（无 LLM 或 LLM 解析失败时兜底）。"""
    m = message

    def has(*kws):
        return any(k in m for k in kws)

    # 新增类操作优先于查询
    if has("待办", "todo", "任务") and has("加", "新增", "添加", "记", "帮我记"):
        return {"intent": "todo_add", "params": {"content": m}}
    if has("便签", "笔记", "note") and has("加", "新增", "添加", "记", "写"):
        return {"intent": "note_add", "params": {"content": m}}
    if has("链接", "网址", "link") and has("加", "新增", "添加", "收藏"):
        return {"intent": "link_add", "params": {"title": "", "url": ""}}
    if has("待办", "todo", "任务") and has("完成", "标", "做完", "搞定"):
        return {"intent": "todo_done", "params": {}}
    if has("待办", "todo", "任务"):
        return {"intent": "todo_list", "params": {}}
    if has("便签", "笔记", "note"):
        return {"intent": "note_list", "params": {}}
    if has("链接", "网址", "link"):
        return {"intent": "link_list", "params": {}}
    if has("算法", "题", "leetcode", "刷题"):
        return {"intent": "algorithm_search", "params": {}}
    if has("订阅", "rss", "b站", "bilibili"):
        return {"intent": "subscription_list", "params": {}}
    # 校招岗位（外面在招的，来自秋招网抓取）。
    # 放在求职追踪之前判断：用户说「有哪些岗位」问的是外面有什么，
    # 说「我的投递进度」问的才是自己投了什么。
    if has("岗位", "校招", "招聘", "在招"):
        if has("抓取", "刷新", "更新一下", "拉取"):
            return {"intent": "posting_refresh", "params": {}}
        return {"intent": "posting_list", "params": {}}
    # 求职追踪：无 LLM 时只降级到只读查询。
    # 写操作（记一条投递 / 改进度）需要从自然语言里抽出公司、岗位、阶段，
    # 规则匹配很容易张冠李戴——宁可让用户手动点，也不写一条「公司名是整句话」的脏数据。
    if has("求职", "投递", "秋招", "春招", "社招", "有哪些面试"):
        if has("统计", "概览", "几家", "多少家", "回复率"):
            return {"intent": "job_stats", "params": {}}
        return {"intent": "job_list", "params": {}}
    if has("消息", "通知", "动态"):
        return {"intent": "message_list", "params": {}}
    if has("总结", "消化", "复盘"):
        return {"intent": "summary_generate", "params": {}}
    if has("全貌", "概览", "看看", "有哪些", "都有什么", "所有"):
        return {"intent": "query_all", "params": {}}
    return {"intent": "chat", "params": {}}


def _fallback_chat(messages: List[Dict[str, str]]) -> str:
    """无 LLM 时的降级回复文本（非流式与流式共用，保证两种路径文案一致）。"""
    last = messages[-1]["content"] if messages else ""
    return (
        "（当前未配置大模型，我暂时只能做关键词级的简单响应。"
        "在前端「设置」里配置 API Key 后，我就能真正理解并操作你的工作台。）\n\n"
        f"你刚才说：{last}"
    )


def chat(messages: List[Dict[str, str]], llm_config=None) -> str:
    """多轮对话回复。messages 形如 [{"role":"user"/"assistant","content":...}]。"""
    if _llm_available(llm_config):
        full = [{"role": "system", "content": CHAT_SYSTEM}] + messages
        return _call_chat(full, llm_config)
    return _fallback_chat(messages)


# ---------------------------------------------------------------------------
# 流式输出（供 SSE 端点使用）
# ---------------------------------------------------------------------------

# 规则降级文本的切块大小与节奏（模拟打字机，纯观感，不影响语义）
_FALLBACK_CHUNK = 12
_FALLBACK_DELAY = 0.015


def _chunk_text(text: str, size: int = _FALLBACK_CHUNK, delay: float = _FALLBACK_DELAY):
    """把整段文本切成小块逐块产出，用于无 LLM 时的"伪流式"（观感一致）。"""
    for i in range(0, len(text), size):
        yield text[i : i + size]
        if delay:
            time.sleep(delay)


def _call_chat_stream(messages: List[Dict[str, str]], llm_config=None, temperature: float = 0.3):
    """流式调用 OpenAI 兼容接口（stream=true），逐块产出 delta 文本。

    解析的是标准 SSE 响应：
        data: {"choices":[{"delta":{"content":"你"}}]}
        data: [DONE]
    任何单行解析失败都跳过而不是中断——流式场景下宁可少一块，也不要整体失败。
    """
    cfg = llm_config or {}
    base = (cfg.get("baseUrl") or config.LLM_BASE_URL).rstrip("/")
    url = base + "/chat/completions"
    api_key = cfg.get("apiKey") or config.LLM_API_KEY
    model = cfg.get("model") or config.LLM_MODEL

    payload = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "stream": True,
    }
    headers = {"Authorization": f"Bearer {api_key}"}

    with httpx.Client(timeout=httpx.Timeout(180.0, connect=10.0)) as client:
        with client.stream("POST", url, json=payload, headers=headers) as resp:
            resp.raise_for_status()
            for raw in resp.iter_lines():
                if not raw:
                    continue
                line = raw.strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    obj = json.loads(data)
                except json.JSONDecodeError:
                    continue
                choices = obj.get("choices") or []
                if not choices:
                    continue
                delta = choices[0].get("delta") or {}
                piece = delta.get("content")
                if piece:
                    yield piece


def chat_stream(messages: List[Dict[str, str]], llm_config=None):
    """流式多轮对话：逐块产出回复文本。

    - 有 LLM：真实 token 流；
    - 无 LLM：把规则降级文本切块产出，保证前端流式渲染逻辑始终一致。
    """
    if _llm_available(llm_config):
        full = [{"role": "system", "content": CHAT_SYSTEM}] + messages
        yield from _call_chat_stream(full, llm_config)
        return
    yield from _chunk_text(_fallback_chat(messages))
