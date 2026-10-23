# 动森与塞尔达主题

为 `xiaomi-freeguy-home` 分支制作的两套外置主题，每套包括壁纸、16 个应用卡片和配套时钟 / 顶栏样式。应用名称、启动动作、隐藏列表和程序坞顺序沿用电视原有配置。

| 主题 | 目录 / ID | 视觉 |
| --- | --- | --- |
| 动森 · 岛屿来信 | `animal-crossing` | 黄昏岛屿、奶油色时钟、叶片纹样和完整角色卡片 |
| 塞尔达 · 旷野与天空 | `zelda-wilds` | 海拉鲁暖色夕照、暖灰绿卡片与沙金焦点、12 位角色与四神兽的官方透明素材 |

在电视首页打开 **设置 → 简洁桌面设置 → 主题**，用方向键选择并确认。按 Back 或 Home 返回即可应用。原有奥德赛主题仍可随时选回。主题切换不会改动已经保存的应用顺序、列数、程序坞下方行数和动画偏好。主动选择“跟随主题”才恢复主题默认值；固定壁纸选择仍随主题重置。

动森 v2 与塞尔达 v3 默认设置“程序坞下方行数”为 0，让首页 Dock 靠近底部，露出壁纸主体。向下仍可浏览所有应用，Home 返回底部 Dock；已有的手动行数优先于主题默认值。

## 构建与导入

```sh
ANDROID_HOME=/path/to/android-sdk ./launcher/build-freeguy.sh
python3 tools/theme_pack.py pack theme-packs/animal-crossing dist/animal-crossing-v2.zip
python3 tools/theme_pack.py pack theme-packs/zelda-wilds dist/zelda-wilds-v3.zip

TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/animal-crossing-v2.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/zelda-wilds-v3.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py list --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py activate animal-crossing --package com.ts.launcher
```

首次安装 APK 后再导入 ZIP；上面的 `install` 安装的是主题包。新版 ZIP 可直接更新同 ID 的旧主题。本轮暖色卡片、顶栏和偏好修复需要同时更新 APK，壁纸与默认布局由 ZIP 提供。加 `--activate` 可在导入成功后直接切换。电视有系统文件选择器时，也可以使用设置中的“导入主题 ZIP”。工具沿用本项目的 ADB 连接和密钥。

ZIP 在导入时验证，再复制到应用私有的主题库中。资源独立于 APK，可更新、离线使用；不会持续读取 NAS 或直接挂载共享目录。ZIP 不能带代码或修改系统设置；结构、大小、路径和卡片图片边界不合法时，现有主题继续保留。可读出当前版本的旧资源目录保持不变，避免切换时读到半套素材。

完整规范见 [theme-pack-format.md](../docs/theme-pack-format.md)。素材来源、设计说明和重建记录见 [prompts.md](../artwork/theme-packs/prompts.md)。这两套是个人分支的同人主题，与原作方无官方关联。
