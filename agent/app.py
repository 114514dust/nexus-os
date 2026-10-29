"""Agent 服务入口（FastAPI）。

对外暴露：
- GET  /health     健康检查
- POST /summarize  接收 Java 聚合好的当天数据，走 LangGraph 编排图生成总结

服务无状态、不落库：输入当天数据，输出总结文本，落库由 Java 后端负责。
"""
from __future__ import annotations

from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

import config
from graph import compiled_graph

app = FastAPI(title="Nexus Agent", version="0.1.0")


class MessageItem(BaseModel):
    source: str = "unknown"
    sender: str = ""
    content: str = ""


class TodoItem(BaseModel):
    content: str = ""
    done: bool = False


class NoteItem(BaseModel):
    content: str = ""


class SummarizeRequest(BaseModel):
    date: str
    messages: List[MessageItem] = []
    todos: List[TodoItem] = []
    notes: List[NoteItem] = []


class SummarizeResponse(BaseModel):
    summary: str
    llm: str


@app.get("/health")
def health() -> dict:
    return {"status": "up", "llm": "enabled" if config.llm_enabled() else "rule-based"}


@app.post("/summarize", response_model=SummarizeResponse)
def summarize(req: SummarizeRequest) -> SummarizeResponse:
    state = {
        "date": req.date,
        "messages": [m.model_dump() for m in req.messages],
        "todos": [t.model_dump() for t in req.todos],
        "notes": [n.model_dump() for n in req.notes],
        "raw_text": "",
        "summary": "",
    }
    result = compiled_graph.invoke(state)
    return SummarizeResponse(
        summary=result["summary"],
        llm="enabled" if config.llm_enabled() else "rule-based",
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=config.HOST, port=config.PORT)
