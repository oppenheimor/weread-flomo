# 全字库正楷体

来源：用户提供的 `QuanZiKuZhengKaiTi.zip`，原文件 `TaiWanQuanZiKuZhengKaiTi-2.ttf`。

- `QuanZiKuZhengKaiTi.ttf` 是原始字体，不属于 public/，不会随 Vite 构建发布。
- `scripts/build-display-font.py` 使用 fonttools 生成 `public/assets/fonts/quanzi-kai/*.woff` 和 `web/fonts.css`。
- 常用界面字符合为 core，其余字符按 Unicode 区块分片，通过 unicode-range 按需加载；仅声明原字体支持的 27,547 个汉字与中文标点；英文、数字及其他字符回退到系统字体。
- 品牌、书架标题及书名使用正楷；设置弹窗（包括标题）、正文及操作控件统一使用系统字体。
- 使用 `font-display: swap`，字体未完成加载时不隐藏文本。

压缩包没有附带许可证文件；嵌入权限位允许预览/编辑嵌入，但不替代完整授权条款。对外发布或分发前应另行核实授权。
