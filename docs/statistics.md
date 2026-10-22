# 数据统计

入口：右上角账号菜单 → 数据统计。独立页面 `#statistics`，支持刷新、浏览器前进后退和返回书架。

## 统计口径

- `GET /api/statistics`，使用当前登录账号，不接受外部 user ID。
- 仅统计 delivery 状态为 sent 的 delivery_source；每个来源键唯一，划线和想法分别计数。
- AI 整理同步认领的新来源计入；已经同步的来源保留原投递日期，重复整理不重复计数。
- 日期取 delivery.updated_at，按 Asia/Shanghai 划分；人工确认成功的待核对记录归入确认当天。
- 累计书籍由来源键中的 bookId 去重；旧版无法解析的来源仍计入笔记数，但不猜测书籍。
- 三项累计指标覆盖全部历史，热力图按所选年份展示。没有记录的年份显示零；未来日期以空心格区分。
- 固定颜色区间：0、1–5、6–15、16–30、31+ 条，跨年份可比较。

## 界面

沿用现有主题与字体，三列累计数字、全年周网格、与导航栏账号菜单同款的年份下拉菜单；不增加图表依赖。手机在图表内部横向滚动。日期支持悬停、点击和键盘方向键，Tab 不必遍历全部日期。年份菜单支持方向键、Home/End、Escape 和点击外部关闭。统计口径保留在本文档，不在页面底部展示。

设计参考：Pinterest 搜索 `github contribution heatmap dashboard minimal`，参考年度日历排布、低饱和绿阶和轻量工具栏，不复制多卡片仪表盘。

## 验证

- `npm run build`
- `node --experimental-strip-types --test scripts/statistics.test.ts`
- `npm run test:postgres`：真实 PostgreSQL 验证合并来源、AI 去重、待核对排除、北京时间跨日、账号隔离。
- 浏览器使用演示响应检查 1280px 深浅主题、390px 手机、切换年份、键盘、空状态及菜单入口，不向业务数据库写入演示数据。

新增后端接口需要重启 Java 服务后才能在已有开发实例中使用。
