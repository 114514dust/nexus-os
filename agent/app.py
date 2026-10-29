"""Agent 服务入口（FastAPI）。

对外暴露：
- GET  /health     健康检查
- POST /summarize  接收 Java 聚合好的当天数据，走 LangGraph 编排图生成总结
- POST /chat       基于 Workflow 的 AI 对话（意图识别 -> 工具分发 -> 生成回复）

服务无状态、不落库：输入当天数据/对话，输出文本，落库由 Java 后端负责。
"""
from __future__ import annotations

from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

import config
from chat import compiled_chat_graph
from graph import compiled_graph

app = FastAPI(title="Nexus Agent", version="0.2.0")


class MessageItem(BaseModel):
    source: str = "unknown"
    sender: str = ""
    content: str = ""


class TodoItem(BaseModel):
    content: str = ""
    done: bool = False


class NoteItem(BaseModel):
    content: str = ""


class LlmConfig(BaseModel):
    apiKey: str = ""
    baseUrl: str = ""
    model: str = ""


class SummarizeRequest(BaseModel):
    date: str
    messages: List[MessageItem] = []
    todos: List[TodoItem] = []
    notes: List[NoteItem] = []
    llm_config: Optional[LlmConfig] = None


class SummarizeResponse(BaseModel):
    summary: str
    llm: str


def _llm_mode(llm_config: Optional[LlmConfig]) -> str:
    """判断本次总结是否走真实大模型：优先看请求动态配置，再看环境变量。"""
    if llm_config and llm_config.apiKey:
        return "enabled"
    return "enabled" if config.llm_enabled() else "rule-based"


@app.get("/health")
def health() -> dict:
    return {"status": "up", "llm": "enabled" if config.llm_enabled() else "rule-based"}


@app.post("/summarize", response_model=SummarizeResponse)
def summarize(req: SummarizeRequest) -> SummarizeResponse:
    llm_config = req.llm_config.model_dump() if req.llm_config else None
    state = {
        "date": req.date,
        "messages": [m.model_dump() for m in req.messages],
        "todos": [t.model_dump() for t in req.todos],
        "notes": [n.model_dump() for n in req.notes],
        "raw_text": "",
        "summary": "",
        "llm_config": llm_config,
    }
    result = compiled_graph.invoke(state)
    return SummarizeResponse(
        summary=result["summary"],
        llm=_llm_mode(req.llm_config),
    )


class ChatMessage(BaseModel):
    role: str = "user"
    content: str = ""


class ChatRequest(BaseModel):
    message: str
    history: List[ChatMessage] = []
    llm_config: Optional[LlmConfig] = None


class ChatResponse(BaseModel):
    reply: str
    intent: str


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    llm_config = req.llm_config.model_dump() if req.llm_config else None
    state = {
        "message": req.message,
        "history": [{"role": m.role, "content": m.content} for m in req.history],
        "intent": "chat",
        "tool_input": {},
        "tool_result": "",
        "reply": "",
        "llm_config": llm_config,
    }
    result = compiled_chat_graph.invoke(state)
    return ChatResponse(reply=result["reply"], intent=result["intent"])


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.HOST, port=config.PORT)
