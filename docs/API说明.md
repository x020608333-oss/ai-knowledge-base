# API 接口说明

Base URL：`http://localhost:8080`
交互式文档（Swagger UI）：`http://localhost:8080/swagger-ui.html`

---

## 1. 上传文档（知识入库）

`POST /api/kb/upload`  （`multipart/form-data`）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| file | File | 支持 pdf / docx / md / txt，≤ 50MB |

**响应示例：**
```json
{ "code": 200, "message": "success", "data": { "docId": 1, "message": "文档已接收，正在后台解析与向量化，请稍后查询处理状态" } }
```

> 文档为**异步**处理，轮询业务库 `kb_document.status` 字段查看进度（PENDING → PROCESSING → COMPLETED / FAILED，**仅 COMPLETED 才会被检索到**）。默认业务库为 H2 内存库（可通过 http://localhost:8080/h2-console 查看），生产可切换 MySQL。

**curl 示例：**
```bash
curl -F "file=@公司制度.pdf" http://localhost:8080/api/kb/upload
```

---

## 2. 流式问答（SSE）

`POST /api/chat/stream`  （`text/event-stream`）

**请求体：**
```json
{ "sessionId": "可选，首次留空", "question": "公司的年假制度是什么？" }
```

**响应（SSE data 流）：**
- 普通行：回答正文片段（逐段到达，实现打字机效果）
- 末尾一行：以 `__REFERENCES__` 开头的 JSON，为引用来源数组

**引用来源结构：**
```json
{
  "index": 1,
  "fileName": "公司制度.pdf",
  "score": 0.82,
  "snippet": "员工入职满一年可享受带薪年假……"
}
```

**curl 示例：**
```bash
curl -N -H "Content-Type: application/json" \
  -d '{"question":"年假怎么算？"}' \
  http://localhost:8080/api/chat/stream
```

> 💡 **GET 版本**（便于浏览器 / 命令行直接体验，无需构造 POST 请求体）：
> `GET /api/chat/stream?question=年假怎么算？&sessionId=xxx`（`sessionId` 可选，留空则新建会话）

---

## 3. 检索调试接口（不调用 LLM）

`POST /api/chat/retrieve?question=xxx&topK=5`

返回召回的片段列表，用于验证向量检索效果：
```json
{
  "code": 200,
  "data": [
    { "fileName": "公司制度.pdf", "content": "……", "score": 0.82 }
  ]
}
```

---

## 4. 浏览器演示页

启动后直接访问 `http://localhost:8080/index.html`，提供开箱即用的 SSE 流式问答界面。

---

## 前端接入要点（SSE）
- `EventSource` **不支持** POST，需用 `fetch` 读取可读流（`response.body.getReader()`）逐行解析 `data:` 字段。
- 完整前端示例见 `src/main/resources/static/index.html`。
