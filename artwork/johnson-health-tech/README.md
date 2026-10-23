# 乔山 · 品牌展厅

面向公司展厅和接待区电视的离线主题。主题包 ID 为 `johnson-health-tech`；它只提供主题资源，不修改启动器 APK、字体、应用顺序、名称或启动动作。

## 展示设计

首页以七张 1920 × 1080 全屏实景壁纸顺序轮换，每张停留两分钟，一轮约 14 分钟。官方 JOHNSON 白色标识、品牌标识和简短分类叠在低对比度的暗色遮罩上，让照片保持为画面主体，同时避开顶栏与 Dock。

七个品牌场景依次为：

- MATRIX：官方 Hospitality 场景。
- VISION：Stanley A. White 场景。
- HORIZON：官方 7.0 AT 跑步机居家实景。
- BowFlex、Schwinn、SYNCA：各品牌官网照片。
- FUJIIRYOKI：官方商店 D.Core STRATUS-JP 日式环境图。

16 张横幅卡片采用低饱和柔光背景和奶白色 raised glyph。除 `disk` 外，15 张卡片直接复用 `artwork/soft-tv-icons/soft-glow/` 的 glyph 几何；`disk` 使用通用文件夹 glyph，避免旧有的数字化网盘标识。背景在六组配色之间循环，Emby 和播放器仅调整内部强调色以协调卡片；腾讯标识保留品牌本色。

主题默认使用 6 列、Dock 下方 0 行。设备已经保存的列数、下方行数与动画偏好仍优先；用户可在电视设置中手动调整。25 个应用包名映射覆盖常用影音与系统应用，并包含 YouTube；未映射应用保留自身横幅或图标。

## 素材与使用范围

`sources/sources.json` 记录每个本地素材对应的官方来源页面、下载地址和获取日期；`scenes.json` 指定场景、标识和画面裁切位置。官方标识和照片属于各自权利人，项目许可证不为这些素材授予额外权利；公司展示应遵循本公司的品牌素材管理要求。

`design/` 保存可审阅的 HTML 和 SVG，`theme-packs/johnson-health-tech/` 保存最终主题包资源。已在用户的小米 MiTV-MFTR0 上完成七品牌显示、导航和偏好保留验证，记录见 [验收说明](ACCEPTANCE.md)。公司电视仍需按实际型号和安装环境复验。

## 离线重建

重建需要 macOS、Python 3、Google Chrome、系统自带的 `sips`，以及 Homebrew 安装的 `cwebp` / `dwebp`。脚本使用 `/Applications/Google Chrome.app/...`、`/usr/bin/sips` 和 Apple Silicon Homebrew 默认的 `/opt/homebrew/bin/` 路径。壁纸排版使用 PingFang SC；其他平台需要调整工具路径，文字外观也可能不同。

所有重建输入均已在仓库中，构建过程不访问网络。从仓库根目录执行：

```sh
python3 artwork/johnson-health-tech/build_theme.py
mkdir -p backup/johnson-dist
python3 tools/theme_pack.py pack theme-packs/johnson-health-tech backup/johnson-dist/johnson-health-tech-v2.zip
python3 tools/theme_pack.py inspect backup/johnson-dist/johnson-health-tech-v2.zip
```

将 ZIP 放到电视可访问的存储位置，在 **设置 → 简洁桌面设置 → 导入主题 ZIP** 中导入，再选择「乔山 · 品牌展厅」。它需要支持主题包的本项目启动器；导入后资源复制到应用私有存储，可离线使用。
