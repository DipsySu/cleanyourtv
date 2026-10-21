# 小米 MiTV-MFTR0 / freeguy：Android 11 定制分支

本分支 `xiaomi-freeguy-home` 用于个人电视配置，基于上游提交
`3e996739ce0b470b51c1c438bf5d0559d1c9e7eb`。实机为 Android 11 / API 30，
固件 `OS2.0.5.0.RFTMATV`，应用 ABI 为 `armeabi-v7a`。2026-10-21 核对。

同型号 Android 14 的社区记录见 [原设备说明](xiaomi-mitv-mftr0-freeguy.txt)；
两版固件的 Home 行为不同。本文件记录这个分支实际采用的配置与验证范围。

## 桌面与应用入口

底部固定栏顺序：**无线投屏 → 云视听小电视 → 直播电视 → Emby**。

Emby 使用包名 `com.mb.android`。高清播放器保留在普通应用列表，使用
`duokan.mediaexplorer.intent.action.BROWSE_DEVICE_LIST` 打开；未卸载或清空应用。

- 合并 `LEANBACK_LAUNCHER` 和普通 `LAUNCHER` 入口，优先电视入口，并按包去重。
- 将配置中明确指定、能解析且导出的 action 入口加入桌面，解决高清播放器无常规入口的问题。
- 保留应用库，关闭模块卡片发现弹窗。
- 16 个应用使用本地矢量图、柔和渐变和微弱阴影；名称改为卡片下方的原生文字。
- 不增加动态壁纸、后台服务或网络图标下载。

对应主题配置见 [xiaomi-freeguy-soft](../../themes/xiaomi-freeguy-soft/config.json)。
本分支已经把该配置作为内置默认值；无需再向电视推送主题。

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

脚本运行现有单元测试、lint 和 release 构建，仅生成 APK，并将含 16 个矢量图的 APK
预算设为 512 KiB；当前构建约 465 KiB。GitHub Actions 工作流保留上游配置，
本分支的兼容包构建和体积检查通过该本地脚本执行，尚未配置分支自动构建。

已完成适配的电视可按下列方式覆盖更新（将 `TV_HOST` 设为自己的电视地址）：

```bash
adb connect "$TV_HOST:5555"
adb -s "$TV_HOST:5555" install -r launcher/app/build/outputs/apk/release/app-release.apk
adb -s "$TV_HOST:5555" shell am force-stop com.ts.launcher
adb -s "$TV_HOST:5555" shell am start -n com.ts.launcher/com.gathin.tvhome.MainActivity
```

原 `launcher/build-and-install.sh` 与 `tools/theme.sh` 固定使用标准包名，
不应直接用于此兼容包。本分支的构建帮助脚本不会改 Home 或停用原桌面。

## 已验证与恢复

本地检查通过 134 项 Android 单元测试与 206 项 Python 工具检查（包含单独启用的严格数量校验）。
Android lint 为 0 错误、63 条警告，新增 16 个图标资源没有 lint 提示；release 构建成功。

实机验证覆盖 Home、方向键与确认键、重启进入桌面、设置和 Wi-Fi 入口、固定栏应用启动，
以及 HDMI 1 / Apple TV 的原生输入会话。图标更新后再次核对 16 个图形、焦点缩放、
Emby 自动登录和 Home 返回；改固定栏后再次验证第四格启动 Emby。

HDMI 2/3 外接画面、实际手机投屏传输、语音识别、RF/AV 信号接收和长期待机没有完整测试。

回退外观可覆盖安装本机保留的同签名旧 APK，应用数据保持原样。
如需恢复小米桌面，先启用 `com.mitv.tvhome` 并确认原桌面可打开，再停用
`com.ts.launcher`；其他四个包按个人需要恢复原启用状态。不要在没有可用 Home 的情况下
同时停用两个桌面。原安装包、停用前状态及恢复脚本留在本机备份中。
