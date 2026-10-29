# Nexus Agent（Python LangGraph 编排服务）
FROM python:3.13-slim

WORKDIR /app

# 先装依赖，利用 Docker 层缓存
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/

COPY . .

EXPOSE 9001

# 监听 0.0.0.0 以便被 nexus-server 容器访问
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9001"]
