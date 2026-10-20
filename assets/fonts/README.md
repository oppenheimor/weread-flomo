# 全字库正楷体

来源：用户提供的 `QuanZiKuZhengKaiTi.zip`，原文件 `TaiWanQuanZiKuZhengKaiTi-2.ttf`。

- `QuanZiKuZhengKaiTi.ttf` 是原始字体，不属于 public/，不会随 Vite 构建发布。
- `scripts/build-display-font.py` 使用 fonttools 生成带 SHA-256 前缀哈希的 WOFF2 文件和 `web/fonts.css`。构建命令见脚本头部，fonttools / wordfreq 版本已固定。
- 界面展示标题使用小型 core；其余字符依据 wordfreq 3.1.1 的公开中文词频排序，常用字每组最多 64 字，剩余字每组最多 128 字。通过 unicode-range 按需加载，保持原字体全部汉字与中文标点覆盖；英文、数字及其他字符回退到系统字体。
- 新字体 URL 带内容哈希，服务器返回一年 immutable 缓存；内容变更会生成新 URL。旧 WOFF 文件暂时保留供旧版缓存 CSS 使用，当前 CSS 不再引用。
- 品牌、书架标题及书名使用正楷；设置弹窗（包括标题）、正文及操作控件统一使用系统字体。
- 使用 `font-display: swap`，字体未完成加载时不隐藏文本。

压缩包没有附带许可证文件；嵌入权限位允许预览/编辑嵌入，但不替代完整授权条款。对外发布或分发前应另行核实授权。
