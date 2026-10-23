# 乔山主题验收记录

日期：2026-10-23。基于启动器提交 `6afd1ecf8efef95215f406710c73bed1cb973b84`，在独立分支 `dipsy/johnson-health-tech-theme` 制作。

## 交付内容

- `johnson-health-tech`，schema 1，version 1，显示名称「乔山 · 品牌展厅」。
- 七张 1920 × 1080 全屏品牌壁纸、16 张 384 × 216 柔光卡片、一张 960 × 540 主题缩略图；两分钟顺序轮换。
- ZIP：1,002,105 bytes（约 978.6 KiB）。SHA-256：`7ae98464bff4777087744cd847642ea204d5e53eaa3a515109140428c974c217`。
- 全部为外置主题资源，未修改启动器代码或向 APK 添加资源。
- 官方照片和标识的 15 项来源记录、原文件大小及哈希均已核对；23 张 WebP 和一张 JPEG 的哈希与导出报告一致，ZIP 的 26 个成员与主题目录逐字节一致。
- 15 张继承卡片的路径、描边和变换与原柔光素材一致；文件卡片使用通用文件夹图形。

## 自动检查

```sh
python3 artwork/johnson-health-tech/build_theme.py
python3 tools/theme_pack.py pack theme-packs/johnson-health-tech backup/johnson-research/johnson-health-tech-v1.zip
python3 tools/theme_pack.py inspect backup/johnson-research/johnson-health-tech-v1.zip
CLEANYOURTV_STRICT=1 python3 -m unittest discover -s tests -v
git diff --check
```

最终资源生成、打包及协议检查通过；完整 Python strict 测试 250 项通过（37.137 秒）。所有 WebP 已完整解码并验证尺寸和不透明性。未修改 Android 源码，因此本次未重新执行 Android 构建和单元测试。

## 运行检查

使用临时只读 Android 14 手机 AVD，横屏 1920 × 1080、320 dpi、字体缩放 1.0；安装现有 release APK（488,864 bytes）。手机系统手势条和下方应用行的一小部分在截图中可见；该检查不能替代公司电视上的显示、遥控器和性能验收。

- 最终 ZIP 导入并激活成功。七张壁纸从设置页逐一选中并截图，检查品牌标识、产品照片、人物面部与原生时钟、按钮、Dock 的相对位置。
- 六个预览应用为设置、文件、图库、日历、时钟和 YouTube；均使用对应主题卡片。其余未匹配应用使用原生外观。临时 Dock 配置没有写入主题 ZIP。
- ADB 方向键移入 Dock 并聚焦「文件」，确认键启动 `com.google.android.documentsui`，Home 正常返回启动器。
- 将下方行数手动设为 2，切换原版主题再切回乔山，仍为 2。改回 0 后结束并重启启动器进程，仍为 0。
- 自动轮换：激活后不再操作，间隔 179.386 秒的两次截图分别为 MATRIX 和 VISION；确认按顺序自动更换。配置间隔为两分钟，本次没有测量精确切换时刻或完成 14 分钟整轮测试。
- 检查结束时 crash 日志缓冲区为空。

![MATRIX 模拟器截图](validation/emulator-matrix.png)

## 录像与交付

七品牌界面操作录像长 132.974522 秒，H.264、1920 × 1080、28,472,306 bytes。录像仅做无损封装和 faststart，未剪辑；原始与封装后时长相同，完整解码通过。视频 SHA-256：`62204b454557c3ddc3f821dccf4af749f093b953817e3d61ab5a8bd8fbcc2d2b`。它展示手动选择七张壁纸，并非完整 14 分钟自动轮播。

本地 `backup/johnson-delivery/` 包含离线预览页、ZIP、七张实际截图、录像及 JSON 检查记录。运行录像不纳入 Git。交付目录同步到 NAS `/volume3/ShareFiles/cleanyourtv-johnson-health-tech-2026-10-23/`，使用 `SHA256SUMS` 核对。

公司电视尚未连接或安装；家庭电视本次未唤醒、未改动。
