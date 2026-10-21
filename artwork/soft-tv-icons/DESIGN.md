# 设计规格

采用品牌色渐变、左上柔光与两层轻微位移投影。图案在独立坐标空间绘制，再按实际几何边界等比缩放并居中。

| 项目 | 规格 |
| --- | --- |
| 横幅 | 320×180；背景满幅，圆角由桌面容器裁切 |
| 方形 | 256×256；背景圆角58，边角透明 |
| Android | 180×101.25dp；视口320×180，Android 11可用 |
| 前景 | 共用奶白材质，保留少量品牌色；Plex使用深色字标 |
| 投影 | 两层矢量偏移，透明度约14%和24%，无滤镜 |
| 柔光 | 左上径向白光，中心约20%透明度 |
| 对齐 | 使用geometry-bounds.json记录的路径包围盒 |
| 字标 | 按宽度单独适配，避免Plex、Apple TV等显示过小 |

图案、配色、目标宽高的最终值以design-data.json为准。13枚图标保留品牌参考轮廓，27枚为系统符号或风格化重绘。图形不依赖字体、位图或网络资源。

## 清单

| id | 应用/入口 | 类别 |
| --- | --- | --- |
| `cast` | 无线投屏 | 电视工具 |
| `bili` | 云视听小电视 | 当前入口 |
| `live` | 直播电视 | 当前入口 |
| `player` | 高清播放器 | 媒体播放器 |
| `115` | 115生活 | 网盘 |
| `emby` | Emby | 媒体播放器 |
| `tencent` | 腾讯视频 | 国内视频 |
| `weather` | 天气 | 电视工具 |
| `alarm` | 定时提醒 | 电视工具 |
| `voice` | 小爱同学 | 电视工具 |
| `light` | 幻彩随影 | 电视工具 |
| `calendar` | 日历 | 电视工具 |
| `gallery` | 时尚画报 | 电视工具 |
| `manager` | 电视管家 | 电视工具 |
| `settings` | 电视设置 | 电视工具 |
| `video` | 视频头条 | 电视工具 |
| `jellyfin` | Jellyfin | 媒体播放器 |
| `plex` | Plex | 媒体播放器 |
| `kodi` | Kodi | 媒体播放器 |
| `vlc` | VLC | 媒体播放器 |
| `nova` | Nova Video Player | 媒体播放器 |
| `mxplayer` | MX Player | 媒体播放器 |
| `iqiyi` | 爱奇艺/奇异果 | 国内视频 |
| `youku` | 优酷/酷喵 | 国内视频 |
| `mango` | 芒果TV | 国内视频 |
| `cctv` | 央视频 | 国内视频 |
| `dangbei` | 当贝影视 | 国内视频 |
| `youtube` | YouTube | 国际视频 |
| `netflix` | Netflix | 国际视频 |
| `primevideo` | Prime Video | 国际视频 |
| `disneyplus` | Disney+ | 国际视频 |
| `appletv` | Apple TV | 国际视频 |
| `twitch` | Twitch | 国际视频 |
| `spotify` | Spotify | 音乐与短视频 |
| `qqmusic` | QQ音乐 | 音乐与短视频 |
| `netease` | 网易云音乐 | 音乐与短视频 |
| `douyin` | 抖音 | 音乐与短视频 |
| `aliyundrive` | 阿里云盘 | 网盘 |
| `baidunetdisk` | 百度网盘 | 网盘 |
| `quark` | 夸克网盘 | 网盘 |
