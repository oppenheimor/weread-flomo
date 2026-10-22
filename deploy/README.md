# 拾墨服务器部署

## 当前部署：笔记浏览、同步进度与 AI 整理优化（2026-10-22）

- 代码提交 `9e814f1`，镜像 `weread-flomo:20261022-083308-9e814f1`，容器 healthy。
- jar SHA-256：`410e474b017ddec94505b60c5f9413057b86e4ffddf4c24040e03153dd9aad75`，上传后校验一致。
- 新增可复用空状态与插画、可搜索章节菜单、按账号/书籍持久化章节位置、真实同步计数；优化模型设置反馈、文字选区及通用 AI 整理提示词。
- Magpie 网关密钥改为按账号加密入库，浏览器每次调用前通过登录、Origin、CSRF 保护且 no-store 的专用接口读取；不写浏览器持久存储。旧版未保存的密钥需要用户重新填写一次。
- 前端测试与 `/shimo/` 生产构建通过；隔离 PostgreSQL 中 80 项测试通过，无失败、无跳过。V10 仅新增 `book_position` 表，生产迁移成功。
- 切换前确认无运行中 AI 任务或 sending 记录，停服后备份数据库及配置。上线后服务器仍保留 20 条 sent 记录；没有迁移本地账本、改凭证或执行真实 flomo 同步。
- 公网验证：健康检查 ready，HTML/JS/CSS/字体 200，HTTP/2、gzip、静态缓存正常，HTML/会话不缓存；匿名章节位置与模型设置接口 401。三个现有服务均 healthy。未执行登录后的真实模型调用或同步验收。
- 回滚点：`/srv/backups/shimo-before-20261022-083308-9e814f1/`，含已验证可列目录的数据库自定义格式备份、旧镜像配置、Compose、service.env 与旧发布记录。目录及文件仅 root 可读。
- 应用回滚：恢复该目录 `image.env` 到 `/srv/apps/weread-flomo/.env`，执行 `sudo docker compose up -d weread-flomo`。V10 为新增表，可保留；不要恢复旧数据库覆盖上线后的业务数据。未改 Nginx、数据库配置、加密主密钥或其他服务。

## 上一部署：拾墨公开介绍页（2026-10-20 11:05 UTC）

- 代码提交 `e1e6da5`，镜像 `weread-flomo:20261020-110312-e1e6da5`，容器 healthy。
- jar SHA-256：`968cf52e1b69991624c4846d80ceebc4659fa82ffc72b70440652cc87f5ebb70`，服务器与本地一致。
- 新增公开页面 `/shimo/introduction`，应用导航使用羽毛笔图标入口；登录和加载页面不加入口。三张真实截图使用高清无损 WebP。
- 构建通过，隔离 PostgreSQL 中 46 项测试全部通过，无跳过。本次无数据库迁移，未修改数据库配置、密钥、Nginx 或其他服务。
- 公网验证：介绍页及尾斜杠均 200，健康检查和匿名登录会话接口 200，私有 API 仍为 401；三张截图与本地字节一致。浏览器验证标题、灵感来源、三图加载、无横向溢出，介绍页不请求账号 API。未发送笔记。
- 回滚点：`/srv/backups/shimo-introduction-before-20261020-110312-e1e6da5/`，含数据库自定义格式备份（已验证可列目录）、旧镜像标签、Compose 和发布记录。恢复 `image.env` 到应用目录 `.env` 后执行 compose up 即可回滚应用，不应恢复旧数据库覆盖新业务数据。

## 上一部署：字体性能优化（2026-10-20 10:35 UTC）

- 代码提交 `440c45c`，镜像 `weread-flomo:20261020-103246-440c45c`，容器 healthy。
- jar SHA-256：`288e03495767e20db6506ccec6224ab415774efabfb7165d9077b1bfd9637010`。
- 字体改为 253 个按词频分组的 WOFF2 子集，保持 27,547 字覆盖；常用字每组最多 64 字，生僻字最多 128 字。仅核心标题字体提前加载，其他按 unicode-range 请求。
- 同一组截图书名推算的字体下载量从约 10.11MB 降到 899,076 字节（约减少 91%）；这是该样本的资源体积对比，不保证所有书架相同。
- 核心字体 140,324 → 10,156 字节；本次公网单次测量新核心 0.235 秒、普通子集 28,072 字节 / 0.413 秒，实际耗时随网络变化。
- 新字体文件名带内容哈希，仅匹配哈希 WOFF2 的 Nginx location 使用 `public,max-age=31536000,immutable`；旧 WOFF 和其他资源仍保留原短缓存，HTML / API 仍 no-store。保留旧字体兼容旧 CSS，不会随新页面全部下载。
- 构建和 PostgreSQL 全部测试通过；线上验证 WOFF2 文件与本地字节一致、CSS 不再引用 WOFF、preload 正确、长期缓存生效、条件请求 304、匿名 API 401、三个服务 healthy。
- 回滚点：`/srv/backups/shimo-font-before-20261020-103246-440c45c/`，包含数据库、旧镜像标签和旧 IP Nginx 配置。回滚应用时恢复该目录 `image.env` 到应用目录 `.env` 后 compose up；需要撤销缓存策略则恢复 `shimo-ip.conf`，先 nginx -t 再 reload。此版本没有数据库迁移，不要用旧快照覆盖新业务数据。

## 上一版本（2026-10-20 10:14 UTC 更新）

- 代码：GitHub `oppenheimor/weread-flomo`，`main`，提交 `2f9f093af5af28792aa091e710ccf5301975369f`。
- 镜像：`weread-flomo:20261020-101456-2f9f093`，容器 `shimo` healthy。
- jar SHA-256：`028238b76b18660c9c7f0eff2fd20c09e03f2c36c6e3f98126c23fed7f7e3344`，本地与服务器一致。
- 发布前构建通过，隔离 PostgreSQL 中 43 项测试全部通过；生产 Flyway V3–V5 迁移成功。
- 备份：`/srv/backups/shimo-before-20261020-101456-2f9f093/`，包含停服后的数据库自定义格式备份、旧镜像标签、Compose、加密配置和业务数据校验摘要。目录 0700、敏感文件 0600。
- 公网校验：HTTPS/HTTP2、gzip、静态资源及主题初始化脚本 200；匿名业务 API 401；注册缺 CSRF / 错误 Origin 403、空参数 400；原账号登录、17 本书、章节预览、注销及会话隔离通过。没有创建生产测试账号、改动凭证或发送 flomo。
- 原有账号（不含已移除的 singleton 列）、加密凭证、delivery / delivery_source 四张表逐行摘要前后一致。新增 dismissed_source / note_thought 为空。
- 旧镜像仍保留。需要回滚时先停服、备份上线后新增数据，再恢复备份中的 `image.env` 并启动；V4/V5 为新增表，V3 仅移除单账号限制，旧代码仍可使用现有账号登录。不要恢复旧数据库覆盖新用户或新同步记录；旧版不提供新增功能，多账号时不能使用旧版离线账本导入。
- 本次未改 Nginx、域名路由、TLS 证书或其他业务服务。

## 首次部署记录（2026-10-20）

- 访问：**https://14.103.81.73/shimo/**，沿用本机账号和密码。仅 `/shimo` 和 `/shimo/` 下 HTTP 跳转 HTTPS；根路径恢复部署前行为（HTTP 404，HTTPS 跳转原 www 域名）。
- 当前版本：`weread-flomo:20261020-053251-subpath`；容器 `shimo`。首次根路径版本 `20261020-045443` 已替换。
- jar SHA-256：`3c942b45f47f4f58bd28b05250bf59a58e5cc1dba1efe53870e220d654e0d332`。
- 来源为本机工作树构建（含未提交修改），不是一个已发布 Git commit。完整 Dockerfile 已补上 public 静态资源复制。
- 本地后端已停止，数据库原样保留。**服务器为后续同步记录的唯一写入点，不要恢复本地发送服务并行使用**，否则两套账本会分叉。
- 未改动域名 DNS、原域名站点或上传服务；没有新增公网应用端口。

## IP 证书

临时使用带 `IP:14.103.81.73` SAN 的自签名 HTTPS 证书，**不是公信 CA 证书**。浏览器首次访问会提示不受信任。核对证书后可在客户端信任该证书；没有自动修改本机信任库。不要通过 HTTP 输入账号或个人 API 凭证。

公钥证书已通过 SSH 下载至本机 `~/.config/shimo/server-ip.crt`；私钥只在服务器 root 可读目录。

- SHA-256 指纹：`43:6E:8D:7F:DF:1C:92:FB:7F:8E:BB:30:A2:49:C6:13:EB:71:FD:A6:B8:31:68:DE:0F:A9:65:6C:B0:EF:29:66`
- 到期：2027-10-20 04:56:53 UTC；没有自动续期。
- macOS 可打开上述证书导入登录钥匙串，核对指纹后在证书信任设置中信任其 SSL 用途。是否信任由用户决定。

命令行验证不需要忽略 TLS 校验：

```bash
curl --cacert ~/.config/shimo/server-ip.crt https://14.103.81.73/shimo/health
```

## 网络与目录

- `/srv/apps/weread-flomo/`：Compose、镜像标签 `.env`、`release.txt` 和 `releases/<版本>/image/` 构建产物。
- `/srv/config/weread-flomo/service.env`：root:root 0600；目录 0700。保存数据库独立密码与凭证加密主密钥，不保存个人 API 明文、登录密码。
- 宿主机 PostgreSQL 16：专用 `weread_flomo` 库及同名普通角色；仍只监听 `127.0.0.1:5432`。
- 应用使用 host network，绑定 **172.19.0.1:18081**（Docker 私有 bridge 网关），不绑定公网地址。
- UFW 仅允许 `br-39d7fbc5cc33` 上来自 `172.19.0.0/16`、到 `172.19.0.1:18081` 的 TCP；没有开放公网 18081。
- `/srv/infra/nginx/conf.d/shimo-ip.conf`：Nginx IP 专属虚拟主机；443 的默认 TLS 证书用于无 SNI 的 IP 客户端，原域名 SNI 路由不变。
- `/srv/infra/nginx/certs/shimo-ip.{crt,key}`：临时 IP 证书及私钥。
- `APP_PUBLIC_ORIGIN=https://14.103.81.73` 保持纯 origin（不能加路径），`COOKIE_SECURE=true`；`SERVER_SERVLET_CONTEXT_PATH=/shimo`，`SERVER_SERVLET_SESSION_COOKIE_PATH=/shimo`。前端 `APP_BASE_PATH=/shimo/` 构建，API、图片、字体及登录 Cookie 全部限制在子路径。
- 非 root、只读 rootfs、无 capability、768MiB 上限；健康检查使用 `/shimo/health`，支持自动重启。

## 迁移与验收

用户明确授权迁移账号、加密凭证和同步记录。停掉本地 Java 后执行一致性快照，从本地 PostgreSQL 17 恢复到服务器 PostgreSQL 16；只移除 PG16 不支持的会话设置 `SET transaction_timeout = 0`，目标库事务恢复成功。

- 迁移 1 个账号、2 项加密凭证、1 条 `sent` 记录及其 1 条 source 映射、2 条 Flyway 迁移记录。
- 五张表按固定顺序序列化后逐字节比较一致；保留原用户 UUID 和原加密主密钥。
- 不迁移 Spring Session 和登录限速桶，用户需重新登录。
- 本机旧 SQLite `data/sync.sqlite` 没有合并或自动导入，本次迁移的是当前 Java 应用 PostgreSQL 账本。
- 本地构建通过，25 项测试全部通过，jar 包含 logo、登录插画及 58 份字体子集。
- 公网使用显式受信证书验收：HTTPS 登录页、静态图片、匿名 API 401、原账号登录、Secure/HttpOnly/SameSite=Strict Cookie、CSRF/Origin 403、读取 17 本书、真实笔记预览、注销 204 后 API 401。
- 容器重启后账号、凭证、账本仍在；`shimo`、`edge-nginx`、`sparkden-tos` 均 healthy，原 TOS 健康接口服务器侧返回 200。
- 未执行 flomo 发送、修改凭证或清理账本。浏览器仍需用户处理自签名证书信任；本次公网 API 验收不等于公信浏览器证书验收。

## 发布与回滚

下一次更新：先 `APP_BASE_PATH=/shimo/ npm run build:server` 和测试（完整 Dockerfile 对应 `--build-arg APP_BASE_PATH=/shimo/`），安全传输 jar 到新版本目录，以 `Dockerfile.runtime` 构建独立标签，修改服务器应用目录 `.env` 的 `SHIMO_IMAGE`，执行：

```bash
ssh volc-shanghai 'cd /srv/apps/weread-flomo && sudo docker compose up -d'
```

不要运行 `docker compose config` 将包含秘密的展开配置输出到聊天/日志。不得重建数据库或更换加密主密钥。数据库迁移须单独检查向后兼容性。

首次部署回滚点：`/srv/backups/shimo-before-20261020-045443/`（0700），含原 Nginx 配置、原防火墙规则清单、受保护的迁移 SQL 和恢复日志。

如需撤销本次首次部署：

1. `cd /srv/apps/weread-flomo && sudo docker compose stop`，保留数据库和密钥。
2. 将新增 `/srv/infra/nginx/conf.d/shimo-ip.conf` 移出 `conf.d`，`sudo docker exec edge-nginx nginx -t` 成功后 reload；不要替换其他业务已更新的配置。
3. 移除本次新增的精确 UFW bridge 规则（先核对 `ufw status numbered`，不要按过期编号删除）。
4. 不自动恢复本地发送：如服务器已新增记录，须先回迁最新账本，避免重发。

## 子路径切换验收与回滚点

2026-10-20 用户确认将应用迁至 `/shimo/`。切换前停服务并备份数据库/环境/Compose/旧 IP 路由，备份位于 `/srv/backups/shimo-before-20261020-053251-subpath/`（0700）。旧根路径登录会话已作废，需要重新登录；业务表和凭证未修改。

公网严格验证证书后确认：根路径 HTTP404 / HTTPS301 到原域名、原 `/api` 和 `/assets` 不再路由到拾墨、`/shimo` 跳转到 `/shimo/`、HTML/JS/CSS/字体/图片均200、登录 Cookie Path=/shimo、匿名401、CSRF/Origin403、读取17本书和预览、注销后401全部通过。四张业务表切换前后哈希一致，未发送笔记。

如必须回滚该版本，停止应用后一起还原上述备份的 `image.env`（到应用目录 `.env`）、Compose、service.env 和 IP Nginx 配置，再启动并校验 Nginx/reload；此操作会恢复旧根路径部署，需用户另行确认。仅路由/配置回滚不需要恢复数据库，不得用旧快照覆盖新同步记录。

## 静态资源性能优化（2026-10-20）

仅更新 IP 虚拟主机并校验后热重载 Nginx，未重启应用或改业务数据：

- 启用 HTTP/2；gzip 增加 Spring 返回的 `text/javascript` MIME，压缩级别5，Vary: Accept-Encoding。
- 只有 `/shimo/assets/` 公共资源使用 `public, max-age=3600, must-revalidate`。未使用代理共享缓存；HTML、登录会话及 API 继续 no-store，未缓存私人数据。
- 所有静态资源只缓存1小时，不设 immutable，避免无内容哈希的 logo/字体更新后长期过期；新 JS/CSS 文件名自带哈希。
- 真实 gzip JS 从253421字节降至80446字节（约减少68%）；邻近测量下载从4.55秒到1.60秒，公网波动明显，不视为固定加载时延保证。
- 验证 HTTP/2、gzip、静态缓存、字体条件请求304、匿名API401/no-store、根路径原行为以及原TOS health；三个容器保持 healthy。未执行笔记发送。
- 回滚配置：`/srv/backups/shimo-nginx-performance-20261020-055134/shimo-ip.conf`。仅还原该文件，`nginx -t` 成功后 reload，不需要恢复数据库。
- 回归检查：`python3 scripts/check-deployment-performance.py`（使用本机 `~/.config/shimo/server-ip.crt` 校验服务器证书，只读请求；执行需可访问服务器）。

## 仍需处理

- 用户自行信任临时 IP 证书；备案完成后再确认域名 HTTPS 切换，不自动恢复 DNS。
- 本次本机迁移快照：`~/.local/share/shimo/backups/20261020-045443/database.sql`（0600，父目录0700）；独立主密钥备份：`~/.config/shimo/credential-encryption-key`（0600）。临时传输目录已清理。
- 当前只有本次迁移快照和本机旧库，没有持续异地备份、证书续期或灾备演练。主密钥备份需继续妥善保护，丢失后无法解密凭证。
- 字体授权仍需确认；原字体压缩包无授权文件，不能把本次部署视为授权核实。
