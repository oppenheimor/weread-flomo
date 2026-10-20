# flomo 写入接口核对

## 一手来源

- https://help.flomoapp.com/advance/api.html
- https://flomoapp.com/mine?source=incoming_webhook
- 登录后进入「设置 → MCP / API → API」，页面提供专属地址及开发示例。

## 已核对的页面内容

- 使用专属 API URL，HTTP POST。
- 请求头 `Content-Type: application/json`。
- 请求 JSON：`{"content":"正文内容","content_type":"markdown"}`。
- 支持 Markdown 格式。
- 每天通过 API 最多发布 100 条。
- API URL 本身包含密钥，不应泄露；不在此文件记录用户的真实地址。
- 官方帮助页说明需要 PRO。

## 本项目处理

- URL 限制为 `https://flomoapp.com/iwh/<id>/<key>/`，禁止重定向及任意代理地址。
- 每批最多 100 条，串行发送，相隔至少 1.1 秒；这只是本地保守限速，不声称是官方每秒限额。
- 官方页面未完整列出响应结构。用户授权一次实际写入后已核实成功回执为 HTTP 200，JSON 顶层 `code: 0`、`message: "已记录"`、`memo: { slug: "…", ... }`。因此验收条件为 HTTP 2xx、`code === 0`、非空 `memo.slug`。
- 曾错误检查 `data.slug`，导致成功被标记为不确定并中断批次。已经用实测结构建立回归测试并修正；不得恢复猜测的 data 包装层。
- 响应不符合验收条件、非零错误码、HTTP 错误、网络异常均停止批次。当前统一将这类条目标记为结果不确定并阻止重发；即便部分错误意味着明确拒绝，也优先避免重复，后续可根据经核实的业务错误码细化。
- 不保存原始响应，不回传远端错误正文或完整密钥 URL。
- 已在用户明确授权下，发送其待发送笔记中的第二条进行验证，仅发送一次，取得成功回执。未重发第一条（用户已核对成功），未发送第三条。前两条本地账本均已修正为 sent。只输出响应结构和成功码用于诊断，没有保存原始回执或暴露专属 URL。
