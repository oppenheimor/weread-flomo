# 会话交接：微信读书 → flomo

## 最新确认

- 前端 Vite + TypeScript + React；后端 Java 21 / Spring Boot 3.5.16；PostgreSQL。
- 单账号本人使用，不开放注册。2026-10-20 用户授权部署并迁移账号、加密凭证和同步记录。
- 已部署上海服务器：https://14.103.81.73/shimo/（自签名 HTTPS，需客户端信任）；根路径恢复原行为，接口/资源/Cookie 均使用 /shimo。前端发布构建需 APP_BASE_PATH=/shimo/，后端 SERVER_SERVLET_CONTEXT_PATH=/shimo；Origin 仍为纯 https://14.103.81.73。未恢复域名 DNS。详细版本、证书、回滚和验收见 `deploy/README.md`。本地后端停止，禁止两套账本并行发送。
- 最新要求：个人微信读书/flomo 凭证不能用全局环境变量，必须在登录后按账号配置；列表显示封面、作者和阅读进度。已实现。

## 代码现状

- `backend/`：Spring Security 登录、CSRF、Origin、限速；Spring Session JDBC；Flyway；PostgreSQL 同步账本和单实例 advisory lock。
- `Credentials` + `SecretCipher` + V2 migration：AES-256-GCM 加密凭证，随机 IV，AAD 绑定 user_id/provider。环境变量只保留主密钥 `CREDENTIAL_ENCRYPTION_KEY`。
- `RemoteApis`：按当前用户读取凭证。**不再读取 WEREAD_API_KEY / FLOMO_API_URL 环境变量。**
- `NotebookService`：读取/发送/凭证变更互斥，变更后清理该用户书目与预览，保留同步账本。
- `web/CredentialSettings.tsx`：账号设置，已保存凭证默认固定掩码；点击眼睛调用 POST `/api/account/credentials/reveal`，鉴权、CSRF、按账号读取、no-store。隐藏/失焦/关闭清除查看状态，未完成请求中止，普通状态/保存响应仍不含明文。更换凭证与移除凭证并排展示（移除为红字图标，二次确认）；编辑时隐藏操作入口，保存紧邻输入框。去掉完成、编辑取消按钮，保存后清空草稿。眼睛按钮内边距 6px；读取状态使用无布局占位的辅助提示，切换显示不再撑高弹窗。
- `BookCover.tsx` + Book DTO：封面、作者、readingProgress 和 markedStatus。只允许 cdn.weread.qq.com / wfqqreader-1252317822.image.myqcloud.com；缺图/失败占位，no-referrer。进度按真实数据（0–100）显示为百分比。
- 交互重构（书架版）：`App.tsx` 书架首页（自动加载、未同步角标、只看未同步）；`NotesDrawer.tsx` 右侧抽屉（自动拉取、待发送/已同步/需核对标签、待发送默认全选、底部固定操作栏）；`CredentialSettings.tsx` 改为设置弹窗；`Sheet.tsx` 原生 dialog 封装。`api.ts` 的 `exclusive` 串行请求，配合后端任务锁避免 409。
- 后端新增：`/api/books` 附带 `sync`（按书统计账本来源条数），`GET /api/sync`，`POST /api/resolve`（核对 uncertain：delivered=true 标 sent；false 删除该记录回到 ready，不自动重发）。PreviewNote 增加 quote/thought/sources 仅供展示，发送正文 content 不变。
- 旧 Node 后端已移除；一次性 TS 脚本用于本地初始化/迁移，不是业务后端。

## 本地环境已配置（不能再说未配置）

- Docker 独立数据库 `weread-flomo-postgres`，127.0.0.1:25432，数据库/用户 weread_flomo，卷 weread-flomo-pgdata。随机数据库密码已写 .env 并通过 TCP 连接验证。
- `.env` 已设置 ADMIN_USERNAME=admin、随机 ADMIN_PASSWORD。账号是否已创建以数据库实际状态为准，不重置密码。
- FRONTEND_PORT=5175、APP_PUBLIC_ORIGIN=http://127.0.0.1:5175，COOKIE_SECURE=false。
- 已生成 CREDENTIAL_ENCRYPTION_KEY 到 .env（0600），没有输出密钥，不能重新生成覆盖。
- 旧个人 API 密钥仍保留在 .env 供用户复制，没有自动迁移到账号。新后端忽略它们，用户重启后登录“账号设置”保存，再自行删除旧字段。
- 不在聊天中展示任何真实密码/密钥，也不读取整个 .env 回显。

启动：`npm run backend:dev`（18081），`npm run dev`（5175）。现在需要重启 Java 才能加载新主密钥、V2 schema 和新 API。前端页面刷新即可；必要时重启 Vite。

## 验证

- `npm run typecheck`、`npm run build:server` 通过。
- `npm run test:postgres`：**25 项全部通过**（16 核心 + 4 加密 + 5 PostgreSQL/HTTP）。默认 npm test 跳过未设置测试数据库的 5 项。
- 浏览器隔离账号验证了设置保存、不回显、删除；真实公开 CDN 封面加载成功；390px 无横向溢出。测试 Java/数据库/浏览器空间均已清理。
- 为核对封面/进度，只读调用了旧本地微信读书密钥获取少量概览字段，没有保存原始响应。**本轮没有写入 flomo，也未改动真实账号凭证数据库或云服务器。**

## 仍待办

- 用户在新版“账号设置”保存个人凭证并验证实际拉取。Java 完整真实发送仍需明确确认后测试。
- 旧 `data/sync.sqlite` 保留；`npm run export:ledger -- data/ledger-export.json` + Java `--app.import-ledger=...` 可显式迁移，未自动执行。同步前核对旧记录避免重复。
- 2026-10-20 云端镜像部署及公网 TLS/API 验收完成：25 项测试通过；迁移 1 账号、2 凭证、1 sent 记录，逐表核对一致；读取 17 本书及真实预览，未发送笔记。重启后持久性、注销和匿名隔离通过。部署明细在 deploy/README.md。
- 主密钥与数据库需独立安全备份；不支持自动主密钥轮换，随意替换会导致旧凭证无法解密。

## 产品与历史

每条划线一条 memo，首行 `#微信读书/《书名》`，空行、原文；有想法才加空行、`---`、空行、想法。独立书评/想法单独发送。手动预览勾选确认；不保存原始 JSON；成功不重发，不检查变化。

flomo 成功回执必须检查 **顶层 memo.slug**（code=0），不是 data.slug。此前 data.slug 误判已修复，回归测试保留。旧版用户授权过一次第二条发送测试；后来用户清过本地 sent 记录并可能再次重试，不能假定旧库为空。

用户偏好简洁回答，已授权开发；不要反复问是否开始。
