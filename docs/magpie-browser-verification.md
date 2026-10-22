# Magpie 浏览器直连验证

## 实现边界

- Magpie 的 `/models` 和 `/chat/completions` 请求从浏览器发往本机回环地址。云端模型仍使用原有后端传输层。
- 浏览器调用需要 Gateway key；缺少密钥时提示先配置，不发出请求。已填写的 key 仅在当前标签页内存中保存，不发送到应用后端，不写浏览器持久存储；刷新后清除。
- 服务端生成提示词、保留可信来源快照，校验浏览器回传的完整结果；任务 ID、账号隔离、覆盖校验、幂等完成与过期回收由服务端负责。
- 新增 V9 数据库迁移，清除旧 Magpie 服务端凭证；上线需要同时更新前后端及迁移数据库。不需要 AI_TRUSTED_HOSTS。

## 已执行

1. `npm run build`：通过。
2. `node --test scripts/magpie.test.ts scripts/ai-request.test.ts scripts/markdown.test.ts scripts/statistics.test.ts`：14 项通过（包含缺少密钥时阻止模型发现与生成的回归测试）。
3. `bash scripts/test-postgres.sh`：隔离 PostgreSQL 容器内执行完整迁移及测试，通过。PostgresTest 16 项包含浏览器任务隔离、无服务端模型调用、草稿、结果校验、重复提交、旧任务拒绝、失败保留旧稿、5 分钟过期回收。
4. `./mvnw -q -Dtest=AiBrowserTest,AiTransportTest,PageControllerTest test`：通过，验证回环地址限制且不放松原有服务端 SSRF 策略。
5. Ego Chromium 实际浏览器验证（独立测试标签页）：
   - 使用 `scripts/magpie-browser-gateway.mjs` 的独立本地 HTTP fixture，通过真正跨域 fetch 取得模型列表及 SSE 中文正文。
   - 在隔离页面挂载真实 ModelSettings 组件；仅拦截应用后端配置 API，未拦截网关请求。
   - 完成「保存配置 → 获取模型 → 测试 → 设为默认」。
   - 捕获配置请求，确认未上传 Gateway key、未调用服务器 `/api/ai/models` 或 `/api/ai/test`、未在 localStorage/sessionStorage 写 key。
   - 调用真实浏览器任务编排，收到 progress 和 complete，未泄漏 key。
   - 测试后关闭标签页并停止 fixture；没有修改真实模型配置，也没有发送 flomo 笔记。

## 真实网关验证与剩余边界

首次测试时，未授权 Origin 的 OPTIONS 请求返回 403，应用正确提示在 `Settings → Network and sharing → Web pages` 授权。随后按用户授权添加 `http://127.0.0.1:5175`，使用专用 Gateway key 从真实浏览器成功取得 68 个模型；没有开启局域网共享。用户后续轮换密钥并确认可用，本文不保存任何密钥。

上述成功流式生成验证仍是**真实浏览器 + 模拟网关**。真实 Magpie 仅验证模型发现，未调用付费模型生成，也不代表所有浏览器均兼容。

上线后需将实际网站的准确 origin 加入 Magpie Web pages，填写有效 Gateway key，按浏览器提示允许本地网络访问，再验证 HTTPS 网站 → 用户本机 Magpie 的真实链路。Safari/Firefox 等浏览器仍需分别验证；不支持时不降级到服务器访问 localhost。
