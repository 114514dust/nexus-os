"""Agent 服务配置。

所有配置通过环境变量注入，保持与 Java 后端一致的"外部化配置"思路。
LLM 相关配置缺失时，服务仍可运行——此时走规则式降级总结（见 llm.py）。
"""
import os


def _get(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


# ---- LLM 配置（OpenAI 兼容接口）----
# 例如 DeepSeek: LLM_BASE_URL=https://api.deepseek.com  LLM_MODEL=deepseek-chat
# 例如通义千问:  LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1  LLM_MODEL=qwen-plus
LLM_API_KEY = _get("LLM_API_KEY")
LLM_BASE_URL = _get("LLM_BASE_URL", "https://api.openai.com/v1")
LLM_MODEL = _get("LLM_MODEL", "gpt-4o-mini")

# ---- 服务 ----
HOST = _get("AGENT_HOST", "127.0.0.1")
PORT = int(_get("AGENT_PORT", "9001"))


def llm_enabled() -> bool:
    """是否配置了真实 LLM；未配置则走规则式降级。"""
    return bool(LLM_API_KEY)
