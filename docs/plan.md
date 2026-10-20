# 当前方案：单账号 Java 服务端

## 已确认

- 前端 Vite + TypeScript + React；**后端 Java + Spring Boot**（用户最新决定覆盖此前全 TS 后端要求）。
- 只有自己的账号，不开放注册。同步状态保存在服务器 PostgreSQL，不再使用本机 SQLite 作为运行存储。
- 部署目标为自己的上海云服务器，参考相邻 `volc-shanghai-devops` 仓库。
- 备案通过前不恢复 DNS、不开放公网服务；先通过 SSH 隧道验证。
- 手动选书、预览勾选、明确确认发送；不做定时任务或已发送内容变更检查。
- 每条划线一条 memo；关联想法附后；独立想法/书评单独发送。
- 不保存微信读书原始 JSON。

## 实现

Java 21 / Spring Boot 3.5.16 / Spring Security / Spring Session JDBC / Flyway / PostgreSQL。

账号密码 BCrypt 摘要、Cookie 会话、CSRF、Origin 校验、登录限速；单账号唯一约束，不提供注册接口。个人凭证在网页登录后的账号设置页填写、更换和删除，AES-256-GCM 按 user_id/provider 加密存入 PostgreSQL。环境变量仅提供 CREDENTIAL_ENCRYPTION_KEY 主密钥，不读取旧全局 API Key。浏览器只可获取配置状态，保存后不回显完整凭证。修改凭证与业务任务互斥，并使该用户所有旧预览失效，不重置同步账本。

PostgreSQL 的 delivery、delivery_source 表按 user_id 隔离，来源键保持旧版本 JSON 数组字符串格式以支持迁移。先提交 sending 再调用远端；成功标记 sent，未知结果标记 uncertain 并停止批次。单实例 advisory lock 防双活，遗留 sending 在启动时恢复为 uncertain。

预览是内存数据，30 分钟过期；账户、会话和发送账本为持久化数据。API 凭证加密保存在服务端数据库，而非用户浏览器。封面来自官方 book.cover，仅允许已核实的官方 CDN；readingProgress 按接口数值展示，未知 markedStatus 不猜测。

## 官方接口

微信读书使用 version 1.0.5 网关，参数平铺。书籍使用 notebooks 分页；划线使用 bookmarklist；个人想法使用小写 bookid、synckey 分页。原始响应只在内存中解析，不保存。

flomo POST JSON content/content_type=markdown；每日 100 条上限。真实旧版联调已经证实成功回执在 **顶层 memo.slug**，不是 data.slug；Java 保留该验收与对应回归测试。

## 当前状态

- Java 和 React 已接通登录/退出及业务接口。
- 18 项核心/加密测试 + 4 项真实临时 PostgreSQL 测试通过。
- 浏览器用隔离账号验证了 Java 打包页面登录、显示主界面和退出。
- 旧 Node 后端已移除；旧 SQLite 文件和 `.env` 未删除、未迁移。
- 提供离线账本导出和 Java 事务导入工具，迁移需明确执行以防重复发送。
- Dockerfile/Compose/SSH 隧道文档已准备，但镜像和云端部署尚未执行。
- 最新一轮只读核实了微信读书的封面/进度字段，实际 CDN 封面加载通过；账号设置使用隔离账号验证。没有发送 flomo 笔记，未修改云服务器、DNS 或公网入口。Java 完整真实同步、服务器重启恢复和公网验收仍待后续执行。
