# 拾墨服务器部署

## 当前部署（2026-10-20）

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
