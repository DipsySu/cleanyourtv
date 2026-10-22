# 小米 MiTV-MFTR0 / freeguy：Android 11 定制分支

本分支 `xiaomi-freeguy-home` 用于个人电视配置，基于上游提交
`3e996739ce0b470b51c1c438bf5d0559d1c9e7eb`。实机为 Android 11 / API 30，
固件 `OS2.0.5.0.RFTMATV`，应用 ABI 为 `armeabi-v7a`。设备信息于 2026-10-21 核对，
2026-10-22 覆盖更新奥德赛角色主题，并将固定栏扩展为六个入口。

同型号 Android 14 的社区记录见 [原设备说明](xiaomi-mitv-mftr0-freeguy.txt)；
两版固件的 Home 行为不同。本文件记录这个分支实际采用的配置与验证范围。

## 桌面与应用入口

底部固定栏顺序：**无线投屏 → 云视听小电视 → 直播电视 → Emby → 腾讯视频 → 高清播放器**。

Emby 使用包名 `com.mb.android`；腾讯视频使用 `com.ktcp.video`，电视应用名为「云视听极光」。
高清播放器使用
`duokan.mediaexplorer.intent.action.BROWSE_DEVICE_LIST` 打开；未卸载或清空应用。

固定栏有独立的六个位置，应用网格默认每行六个，可在「启动器设置」中切换为四个。
切换网格列数不会改变固定栏数量或顺序。默认首页在固定栏下方显示一行应用。
旧版本保存在电视上的列数和可见行数仍会优先生效；覆盖更新后可在设置中选六列、显示一行。

固定栏采用深蓝灰渐变、细边线和柔和底部阴影；选中卡片小幅放大，名称基线保持稳定。
浮动焦点减少白色覆盖，并在跨越不同宽度的行时淡入切换。关闭动态效果后不执行焦点动画。

- 合并 `LEANBACK_LAUNCHER` 和普通 `LAUNCHER` 入口，优先电视入口，并按包去重。
- 将配置中明确指定、能解析且导出的 action 入口加入桌面，解决高清播放器无常规入口的问题。
- 保留应用库，关闭模块卡片发现弹窗。
- 16 个应用使用本地奥德赛角色横幅；名称保持卡片下方的原生文字。
- 顶栏为游戏 HUD 风格（`header_style: odyssey`）：问候语前有力量之月图标和当前壁纸的王国名（主题的 `wallpaper_names`，如「帽子之国 · 下午好」），时钟前有金币，数字为带深色描边和硬投影的 Fredoka 粗圆体，日期放在紫色胶囊里，右上角为奶油色按钮配彩色圆形徽章，选中时变为金色。按钮和固定栏的位置不变。
- 时钟前的金币和固定栏托盘随壁纸切换：主题的 `wallpaper_coins` 换成当前王国的紫色本地硬币（APK 内置 8 张 128×128 `coin_odyssey_*` WebP，共 46,586 字节），`wallpaper_tints` 把托盘换成该王国色调的半透明渐变，去掉黑色底部阴影；无壁纸或无对应条目时仍为金色矢量金币和上述深蓝灰托盘。都市之国的官方硬币是 Pauline 侧像，主题里该条目设为 `02-metro.jpg=ic_odyssey_coin`，显示金色矢量金币；`coin_odyssey_metro` 仍在 APK 内，改回 `=coin_odyssey_metro` 即可启用。
- 8 张王国壁纸每 1 分钟按文件名顺序换一张（`wallpaper_rotation_minutes: 1`，`wallpaper_order: sequential`）：距上次换图满 1 分钟才换，且只在桌面位于前台时检查，回到桌面时检查一次，之后每分钟一次；打开应用或小米屏保盖住桌面时不换图，隔很久回到桌面只前进一张。新图在 1.2 秒内淡入到旧图上，淡入一半（约 0.35 秒）时王国名和金币一起切换，托盘色调随同一过渡渐变；首次打开桌面、电视上固定某张壁纸、关闭动态效果或开启 `reduced_motion` 时直接切换。
- APK 内置 40 张 384×216 不透明 WebP 横幅（`drawable-nodpi`，共 290,632 字节），仅加载实际应用对应图片；方形图标与高分辨率原图不进入 APK。
- 长按应用的 OK 键弹出程序坞菜单（固定 / 移除 / 左移 / 右移 / 应用信息），电视上的选择存在 `dock.txt`。
- 不增加动态壁纸、后台服务或网络图标下载。

对应主题配置见 [xiaomi-freeguy-odyssey](../../themes/xiaomi-freeguy-odyssey/config.json)。
本分支已经把该配置作为内置默认值。8 张王国壁纸和王国名只能通过推送主题送到电视，
2026-10-22 起电视上已推送该主题（推送会把电视上自选的列数、动画和壁纸恢复为主题默认值）。
原 [soft 预设](../../themes/xiaomi-freeguy-soft/config.json)及矢量资源继续保留。

## 小米固件适配

此固件通过包名限制第三方 Home。使用上游已支持的构建参数
`-PlauncherApplicationId=com.ts.launcher`，Java namespace 仍是 `com.gathin.tvhome`。
完整启动组件为 `com.ts.launcher/com.gathin.tvhome.MainActivity`。

这个包名仅用于该固件的本机兼容，并不代表其他同包名桌面的官方发行版。
首次安装应先确认该包名没有被其他应用占用。覆盖更新必须沿用原签名密钥；
GitHub Actions 和另一台电脑的临时 debug 签名通常无法覆盖本机已装版本。

- 设置与网络按钮优先调用小米原生设置 action。
- 标准 HDMI passthrough intent 无处理者时，解析 CEC 设备的物理父端口，再打开小米原生输入源。
- `HW5 / HW6 / HW7` 分别对应 HDMI 1 / 2 / 3，整数 extra `input` 为 `23 / 24 / 25`。
- `HW2` 映射到 AV 的 `input=2`；过滤此机型虚报的 `HW8`，不显示不存在的第四个 HDMI 口。
- 这些输入源和设置特例限于 Xiaomi `freeguy`，其他机型沿用原处理路径。

## 系统清理记录

以下五个包已在实机上通过 `pm disable-user --user 0` 停用，没有删除系统应用或数据：

| 包名 | 作用 |
| --- | --- |
| `com.miui.analytics` | 使用事件统计 |
| `com.miui.tv.analytics` | 电视统计与遥测 |
| `com.xiaomi.tvqs` | 崩溃与性能上报 |
| `com.xm.webcontent` | 网页推广及屏保内容容器 |
| `com.mitv.tvhome` | 原小米桌面；此 Android 11 固件需停用后才能稳定使用兼容桌面 |

这是已执行操作的记录，构建脚本不会自动停用任何包。替换 Home 前须先备份，
安装并确认兼容桌面能启动，且保留可用的 ADB 恢复通道。

保留系统设置、语音、投屏、蓝牙遥控、输入法、电视输入源、系统更新、编解码更新及现有视频应用。
本次没有处理视频应用内部广告，也未证明所有固件开机广告都被移除。

## Emby 与 NAS

实机安装官方 Emby for Android `3.5.71` ARMv7 版本，连接既有 NAS 媒体服务器。
账号由用户在电视上自行登录，沿用原媒体库、观看进度及 IPTV，未更改 NAS 服务端配置。

- 《阿基拉》4K HDR10 HEVC Main10、约 97 Mbps 片源，TrueHD 5.1 音轨与中文 PGS 字幕短时直接播放成功。
  播放器统计为 Direct Play / MediaCodec，用户确认音画正常。
- 统计中的 Display Mode 为 1080/60；没有据此断言物理面板实际输出了原生 4K HDR。
- CCTV-1 H.264 720p / AAC 直接播放成功，首次启动约 21 秒；当时上游直播流有分片等待及 EOF 记录。
- 记住上次用户，并确认从桌面图标可再次进入已登录的媒体主页。

上述为短时测试，没有覆盖所有片源、音频设备或直播频道。客户端 APK、登录信息、
NAS 地址、私人片库截图和设备备份不进入代码仓库。

## 构建与更新

使用 Java 17 或更高版本，配置 Android SDK 34。执行：

```bash
./launcher/build-freeguy.sh
```

脚本运行现有单元测试、lint 和 release 构建，仅生成 APK。奥德赛版本的 APK
预算为 8 MiB；2026-10-22 六入口版本为 5,757,461 字节（约 5.49 MiB），包含 40 张 PNG 横幅；
横幅改为 WebP 后为 781,408 字节，加入 HUD 顶栏和 Fredoka 字体后为 891,389 字节。
原 soft 版本约 465 KiB。GitHub Actions 工作流保留上游配置，
本分支的兼容包构建和体积检查通过该本地脚本执行，尚未配置分支自动构建。

已完成适配的电视可按下列方式覆盖更新（将 `TV_HOST` 设为自己的电视地址）：

```bash
adb connect "$TV_HOST:5555"
adb -s "$TV_HOST:5555" install -r launcher/app/build/outputs/apk/release/app-release.apk
adb -s "$TV_HOST:5555" shell am force-stop com.ts.launcher
adb -s "$TV_HOST:5555" shell am start -n com.ts.launcher/com.gathin.tvhome.MainActivity
```

原 `launcher/build-and-install.sh` 固定使用标准包名，不应直接用于此兼容包。
`tools/theme.sh` 须指定包名：`LAUNCHER_PKG=com.ts.launcher TV_HOST=... tools/theme.sh themes/xiaomi-freeguy-odyssey`
（还原时同样加 `LAUNCHER_PKG`，`tools/theme.ps1` 用 `-Package com.ts.launcher`）；
不指定时会写到标准包名的目录，脚本仍显示成功。本分支的构建帮助脚本不会改 Home 或停用原桌面。

## 已验证与恢复

2026-10-21 的适配检查通过 134 项 Android 单元测试与 206 项 Python 工具检查（包含单独启用的严格数量校验）。
2026-10-22 初次图标更新再次通过 134 项 Android 单元测试、lint 和 release 构建；
lint 为 0 错误、63 条警告及 66 条信息，奥德赛资源没有诊断。此次未修改 Python 工具。

实机验证覆盖 Home、方向键与确认键、重启进入桌面、设置和 Wi-Fi 入口、固定栏应用启动，
以及 HDMI 1 / Apple TV 的原生输入会话。图标更新后再次核对 16 个图形、焦点缩放、
Emby 自动登录和 Home 返回；改固定栏后再次验证第四格启动 Emby。

奥德赛初次安装后逐屏核对 16 个桌面图标及名称、圆角、方向键焦点；当时的四入口固定栏顺序与安装前一致。
从第一格右移三次选中 Emby，确认键进入已登录的媒体主页，Home 键返回马里奥桌面。
实际已安装 APK 与构建产物逐字节一致，包 UID、首次安装时间、默认 Home、辅助功能
和停用应用清单均保持原值。完整浏览后桌面总 PSS 单次快照为 61,941 KiB。
这是短时验证，本次没有重启电视或做内存压力测试。应用库管理弹窗仍显示原应用图标。

六入口版本通过 142 项 Android 单元测试、lint 和 release 构建；lint 为 0 错误、61 条警告及
66 条信息。新增测试覆盖六入口固定栏与四／六列网格的行边界、按视觉位置上下移动、RTL、
不足一行的应用和应用库返回。实机核对六入口顺序、两种网格宽度及跨行方向键；首页保留一行
普通应用，角色图像与此前批准版本一致。四／六列选择在重启启动器后保留，动画开关、最后
一行与应用库的上下返回通过；最终设置为六列、一行可见应用、动态效果开启。
第四格 Emby 再次进入已登录媒体首页，第五格腾讯视频进入现有客户端的协议页，第六格打开
高清播放器。此次未代为接受腾讯协议或测试其视频播放。覆盖安装后的 APK 与构建产物一致，
包 UID、首次安装时间、默认 Home、辅助功能和停用应用清单保持原值。

HUD 顶栏版本（891,389 字节）通过 179 项 Android 单元测试、lint（0 错误、59 条警告）和
210 项 Python 测试，2026-10-22 覆盖安装并推送主题。实机核对问候语显示当前壁纸的王国名、
金币描边时钟、日期胶囊和右上角按钮的金色选中；方向键在固定栏、网格和顶栏按钮之间移动正常，
桌面渲染 0 帧卡顿。长按 OK 的程序坞菜单尚未在实机上操作过。

王国硬币、托盘色调与 1 分钟淡入换图版本（941,965 字节）通过 193 项 Android 单元测试、lint（0 错误、60 条警告）
和 210 项 Python 测试，2026-10-22 覆盖安装并推送主题。实机录像中海之国淡入为森之国时，硬币、王国名和托盘色调同步切换，
画面为逐帧渐变；连续数次换图期间桌面渲染 363 帧，卡顿 5 帧（1.4%）。小米自带屏保约在桌面停留 10 分钟后自动盖上桌面，
此时壁纸暂停轮换，回到桌面后继续。

电视自带 `screenrecord` 在 1080p 下动画期间只能录到约 31 帧／秒（8–40 Mbps 均如此），
桌面本身按 60 帧渲染；显示模式只有 1920×1080 @ 60 Hz。`input keyevent KEYCODE_SLEEP`
可让电视休眠且 ADB 仍可连接，`KEYCODE_WAKEUP` 叫不醒，`KEYCODE_POWER` 可以。

HDMI 2/3 外接画面、实际手机投屏传输、语音识别、RF/AV 信号接收和长期待机没有完整测试。

回退外观可覆盖安装本机保留的同签名旧 APK，应用数据保持原样。
如需恢复小米桌面，先启用 `com.mitv.tvhome` 并确认原桌面可打开，再停用
`com.ts.launcher`；其他四个包按个人需要恢复原启用状态。不要在没有可用 Home 的情况下
同时停用两个桌面。原安装包、停用前状态及恢复脚本留在本机备份中。
