"""可插拔 LLM 客户端。

设计意图：编排图（graph.py）只依赖 `summarize()` 这一个抽象，不关心
"总结"到底由真实大模型完成，还是由规则式降级逻辑完成。这样：
- 无 API key 时链路依然可跑通、可演示；
- 换模型（OpenAI / DeepSeek / 通义）只改环境变量，不改图结构。

这就是"编排与 LLM 解耦"——也是 LangGraph 价值所在：图结构是资产，模型是可替换零件。
"""
from __future__ import annotations

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
