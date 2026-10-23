# 主题包

外置主题包含个人电视主题和公司展厅主题。每套带 16 个应用卡片和配套的时钟 / 顶栏样式，有壁纸的主题同时带壁纸。应用名称、启动动作、隐藏列表和程序坞顺序沿用电视原有配置。

| 主题 | 目录 / ID | 视觉 |
| --- | --- | --- |
| 动森 · 岛屿来信 | `animal-crossing` | 黄昏岛屿、奶油色时钟、叶片纹样和完整角色卡片 |
| 塞尔达 · 旷野与天空 | `zelda-wilds` | 海拉鲁暖色夕照、暖灰绿卡片与沙金焦点、12 位角色与四神兽的官方透明素材 |
| 奥德赛 · 王国之旅 | `mario-odyssey` | 8 张王国壁纸每分钟按顺序淡入，王国名、王国硬币和程序坞托盘色随壁纸切换；游戏 HUD 顶栏，Fredoka 圆体时钟；16 张角色卡片 |
| 柔光 · 电视卡片 | `soft-glow` | 黑色背景、默认顶栏、无壁纸；16 张柔和渐变卡片，即原来的 soft 预设 |
| 乔山 · 品牌展厅 | `johnson-health-tech` | 七张官方全屏实景：MATRIX Hospitality、VISION Stanley A. White、四个品牌官网场景和 FUJIIRYOKI D.Core STRATUS-JP 日式环境图，每两分钟顺序轮换；白色官方标识、低饱和柔光卡片，6 列 / 下方 0 行 |

乔山主题用于公司展厅和接待区。16 张卡片中 15 张复用柔光 glyph 几何，`disk` 使用通用文件夹 glyph；25 个包名映射包含 YouTube。它不修改 APK、字体、应用顺序、名称或启动动作，设备保存的列数、下方行数和动画偏好仍优先。官方素材出处、离线重建与公司真机尚未验证的状态见 [主题说明](../artwork/johnson-health-tech/README.md)。

在电视首页打开 **设置 → 简洁桌面设置 → 主题**，用方向键选择并确认。按 Back 或 Home 返回即可应用。列表第一项「原版主题」是 APK 内置的原始外观：默认细字顶栏、应用自带的横幅或图标、没有壁纸，可随时选回。奥德赛和柔光已移出 APK，只能通过主题包使用。主题切换不会改动已经保存的应用顺序、列数、程序坞下方行数和动画偏好。主动选择“跟随主题”才恢复主题默认值；固定壁纸选择仍随主题重置。

动森 v2 与塞尔达 v3 默认设置“程序坞下方行数”为 0，让首页 Dock 靠近底部，露出壁纸主体。奥德赛和柔光保持原来的 6 列、下方 1 行。向下仍可浏览所有应用，Home 返回底部 Dock；已有的手动列数和行数优先于主题默认值。

奥德赛包自带 Fredoka 时钟字体（`fonts/fredoka.ttf`，固定为 wght 700 / wdth 120），APK 不再内置这个字体。字体采用 SIL Open Font License，许可证见 [FONT-LICENSE-FREDOKA-OFL.txt](FONT-LICENSE-FREDOKA-OFL.txt)。都市之国的官方硬币是 Pauline 侧像，因此这一张壁纸没有硬币条目，显示金色矢量金币。没有设置 `clock_font` 的主题沿用原来的时钟字体。

## 构建与导入

```sh
ANDROID_HOME=/path/to/android-sdk ./launcher/build-freeguy.sh
python3 tools/theme_pack.py pack theme-packs/animal-crossing dist/animal-crossing-v2.zip
python3 tools/theme_pack.py pack theme-packs/zelda-wilds dist/zelda-wilds-v3.zip
python3 tools/theme_pack.py pack theme-packs/mario-odyssey dist/mario-odyssey-v1.zip
python3 tools/theme_pack.py pack theme-packs/soft-glow dist/soft-glow-v1.zip
python3 tools/theme_pack.py pack theme-packs/johnson-health-tech dist/johnson-health-tech-v1.zip

TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/animal-crossing-v2.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/zelda-wilds-v3.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/mario-odyssey-v1.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/soft-glow-v1.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py install dist/johnson-health-tech-v1.zip --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py list --package com.ts.launcher
TV_HOST=TV_IP tools/venv/bin/python tools/theme_pack.py activate mario-odyssey --package com.ts.launcher
```

首次安装 APK 后再导入 ZIP；上面的 `install` 安装的是主题包。新版 ZIP 可直接更新同 ID 的旧主题。奥德赛包用到 `fonts/` 和 `icons/` 两个新目录，需要先装本轮的新 APK，旧 APK 会拒绝整个包。加 `--activate` 可在导入成功后直接切换；`activate legacy` 切回「原版主题」。电视有系统文件选择器时，也可以使用设置中的“导入主题 ZIP”。工具沿用本项目的 ADB 连接和密钥。

ZIP 在导入时验证，再复制到应用私有的主题库中。资源独立于 APK，可更新、离线使用；不会持续读取 NAS 或直接挂载共享目录。ZIP 只能带图片、配置和 TrueType / OpenType 字体，不能带代码或修改系统设置；字体每个不超过 2 MiB，文件头必须是 TrueType 或 OpenType 标记。结构、大小、路径、字体和图片边界不合法时，现有主题继续保留。可读出当前版本的旧资源目录保持不变，避免切换时读到半套素材。

## 从内置奥德赛迁移

旧 APK 把奥德赛作为内置主题，外观配置写在电视外部存储的 `config.json` 里，8 张王国壁纸也在那里。已经装过旧版的电视按下面的顺序更新：

1. 安装新 APK。设置里的第一项改名为「原版主题」。如果电视当时正用它，在第 3 步之前仍是奥德赛顶栏，但角色卡片变回应用自带横幅，硬币变回金色矢量金币，时钟改用系统粗体。
2. 打包并导入奥德赛和柔光主题包，再激活奥德赛（`install dist/mario-odyssey-v1.zip ... --activate`，命令见上）。切换主题会把固定壁纸恢复为主题默认轮换。
3. 推送只含设备配置的旧配置目录：

   ```sh
   LAUNCHER_PKG=com.ts.launcher TV_HOST=TV_IP tools/theme.sh themes/xiaomi-freeguy
   ```

   这个目录没有 `wallpapers/`，推送时会清掉外部存储里旧的 8 张奥德赛壁纸，「原版主题」从此就是原始外观。`dock.txt`、应用排序、隐藏应用、应用库的选择和手动设置的列数 / 行数都不受影响。`theme.sh --reset` 不会清理 `wallpapers/`，不要用它代替这一步。

完整规范见 [theme-pack-format.md](../docs/theme-pack-format.md)。动森和塞尔达的素材来源、设计说明和重建记录见 [prompts.md](../artwork/theme-packs/prompts.md)；奥德赛见 [角色卡片](../artwork/mario-odyssey-icons/README.md)、[王国硬币](../artwork/mario-odyssey-coins/README.md) 和 [王国壁纸](../artwork/mario-odyssey-wallpapers/README.md)；柔光卡片的导出方法见 [柔光电视图标](../artwork/soft-tv-icons/README.md)。动森、塞尔达和奥德赛是个人分支的同人主题，与原作方无官方关联。
