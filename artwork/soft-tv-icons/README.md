# 柔光电视图标

40 枚适用于电视桌面的图标，覆盖现有电视入口、常用媒体播放器、国内外视频平台、音乐和网盘。统一使用柔和渐变、左上柔光与轻微凸起的轮廓。

![完整图板](contact-sheet.png)

打开 [preview.html](preview.html) 可离线预览，支持应用搜索、横幅/方形切换、深浅背景、小尺寸和键盘操作。

## 文件

| 文件 | 用途 |
| --- | --- |
| `svg/` | 40 个 320×180 横幅 SVG，源图满幅，圆角由桌面裁切 |
| `square/` | 40 个 256×256 方形 SVG，带圆角透明边缘 |
| `android/` | 40 个对应的 Android VectorDrawable；180×101.25dp，视口 320×180 |
| `design-data.json` | 图案、品牌参考、颜色与目标尺寸 |
| `geometry-bounds.json` | 图案几何边界，用于等比缩放和居中 |
| `manifest.json` | 图标路径、类别、搜索别名、已确认的包名 |
| `theme-overlay.json` | 现有 16 个电视入口的映射示例 |
| `render_pack.py` / `make_preview.py` | 无网络、仅 Python 标准库的再生成工具 |
| `ACCEPTANCE.md` | 验收范围、结果与适用边界 |
| `soft-glow/` | 柔光主题包的 16 个源 VectorDrawable（`banner_soft_<id>.xml`），即此前桌面 APK 内置的那一组 |
| `export_pack.py` | 把 `soft-glow/` 栅格化为柔光主题包的 384×216 不透明 WebP 横幅 |

完整下载包另含 `png/` 的 640×360 横幅与 `png-square/` 的 512×512 方形，共 80 张 PNG。

## 再生成

在本目录执行：

```sh
python3 validate.py design-data.json
python3 render_pack.py
python3 make_preview.py
```

更改路径几何时，需要同步更新 `geometry-bounds.json`；仅调整配色不需要更改边界。

## 与电视桌面的关系

本目录是可预览、可集成的图标套件。当前电视使用的主题仍以设备记录为准，40 枚套件本身尚未安装到电视；电视上用过的是下面「柔光主题包」一节的 16 张。

`theme-overlay.json` 中的 `banner_studio_*` 需要先把对应 Android 资源合入桌面安装包，才可以应用。它仅包含当前已确认的 16 个应用包名；其他 24 枚素材待安装对应应用、确认包名后再添加映射。图标包不会安装这些应用，也不改变现有 Emby 登录或 NAS 设置。

参考轮廓与风格化重绘分别记录在 `manifest.json` 的 `brand_reference`、`stylized` 字段中；来源见 [SOURCES.md](SOURCES.md)。

## 柔光主题包

[柔光 · 电视卡片](../../theme-packs/soft-glow/)主题包重现此前桌面 APK 内置的柔光卡片（`banner_soft_*`，2026-10-21 随 freeguy 适配加入）。那 16 张是更早手工调好的一组：浅色渐变底、左上柔光、图形下方三层错位投影。它们和本目录 40 枚套件的 `svg/`、`android/banner_studio_*` 配色和图形都不同，逐个对比后没有一张一致，所以主题包以原来的 VectorDrawable 为源。这些文件已从 APK 原样移到 `soft-glow/`。

```sh
python3 -I export_pack.py              # 写入 ../../theme-packs/soft-glow/banners/<id>.webp
python3 -I export_pack.py /tmp/soft    # 先写到别的目录检查
```

`export_pack.py` 只用 Python 标准库，另需本机的 Google Chrome（不在默认位置时用 `CHROME_PATH` 指定）和 libwebp 的 `cwebp`/`dwebp`，不下载任何东西。每张的处理：

1. 把 VectorDrawable 转成等价的 SVG：路径、带平移和缩放的 group、按视口坐标的线性和径向渐变，`#AARRGGBB` 颜色转成透明度。遇到不认识的元素、属性或渐变写法直接报错，不会悄悄画错。
2. 用无头 Chrome 按 768×432（横幅的 2 倍）在黑底上截图，sRGB 色彩。
3. `cwebp -resize 384 216 -q 90 -sharp_yuv -m 6 -noalpha` 编码，与奥德赛横幅同尺寸。
4. 检查 RIFF 结构必须是单一 `VP8 ` 有损块（不带 alpha），再用 `dwebp` 解码确认每个像素都不透明。带 alpha 的横幅会被桌面缩到 90% 放在卡片框上，不透明的才会满幅显示。

16 张全部在临时目录通过后才写入主题包；写入主题包时还会核对主题包 config.json 的 `app_banners` 正好引用这 16 张。结果记录在 `validation/pack-report.json`（每张的 SVG 和 WebP 的 SHA-256、尺寸、字节数、Chrome 与 cwebp 版本）。同一台机器、同一版 Chrome 重复运行，输出逐字节一致。
