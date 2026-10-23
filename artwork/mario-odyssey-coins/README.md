# 奥德赛王国金币

《超级马里奥 奥德赛》里每个王国都有自己的紫色本地硬币（Regional coin，日版“ローカルコイン”），形状各不相同。这里收录与奥德赛主题8张壁纸对应的8枚原图，其中7枚导出为[奥德赛主题包](../../theme-packs/mario-odyssey/)`icons/`里的128×128透明WebP，供奥德赛HUD在时钟前按当前王国显示。

| id | 王国 | 壁纸 | 主题包文件 | 硬币 |
| --- | --- | --- | --- | --- |
| `cap` | 帽子之国 Cap Kingdom | `01-cap.jpg` | `icons/coin-cap.webp` | 礼帽 |
| `metro` | 都市之国 Metro Kingdom | `02-metro.jpg` | 不导出（见下方说明） | 市长Pauline侧像圆币（见下方说明） |
| `sand` | 沙之国 Sand Kingdom | `03-sand.jpg` | `icons/coin-sand.webp` | 倒金字塔 |
| `seaside` | 海之国 Seaside Kingdom | `04-seaside.jpg` | `icons/coin-seaside.webp` | 扇贝 |
| `wooded` | 森之国 Wooded Kingdom | `05-wooded.jpg` | `icons/coin-wooded.webp` | 六角螺母（中间镂空） |
| `lost` | 失落之国 Lost Kingdom | `06-lost.jpg` | `icons/coin-lost.webp` | 叶子 |
| `luncheon` | 料理之国 Luncheon Kingdom | `07-luncheon.jpg` | `icons/coin-luncheon.webp` | 番茄 |
| `moon` | 月之国 Moon Kingdom | `08-moon.jpg` | `icons/coin-moon.webp` | 月石 |

| 内容 | 说明 |
| --- | --- |
| `originals/` | 8张未经修改的源PNG（674×826至1014×844，透明背景） |
| `sources.json` | 每张原图的王国、对应壁纸、页面与图片地址、尺寸、字节数、SHA-256和获取日期 |
| `build_coins.py` | 由原图生成桌面WebP并校验 |
| `validation/coins-report.json` | 每个导出WebP的尺寸、字节数、SHA-256、硬币在画布中的位置与有损误差 |

## 来源

全部取自Super Mario Wiki的[Regional coin](https://www.mariowiki.com/Regional_coin)页面“Models”一节（文件名 `Regional_Coin_<王国>.png`，图片托管在mario.wiki.gallery），2026-10-22获取，与[角色素材](../mario-odyssey-icons/SOURCES.md)同一来源。逐张的文件页和原图地址见sources.json。

都市之国的官方硬币正面就是市长Pauline的侧像（wiki原文：feature Mayor Pauline's portrait）。主题此前已把Pauline从都市壁纸和直播磁贴中换掉，所以主题包不带这枚硬币，`wallpaper_coins`里也没有`02-metro.jpg`，都市之国显示桌面自带的金色矢量金币。原图仍在`originals/metro.png`；想换成都市硬币，把`metro`加进build_coins.py的`PACKED`，重新生成后在主题包的`wallpaper_coins`里加上`"02-metro.jpg": "icons/coin-metro.webp"`，提高manifest版本号后重新打包导入。

选用这一组是因为它是唯一8个王国都齐全、风格一致的透明背景渲染图：同页“Artwork”一节只有帽子、瀑布、沙、都市四国有PNG，其余为SVG；8-bit像素版缺帽子与森之国；游戏截图带场景背景。原图的透明是硬边（没有半透明抗锯齿），其中都市、料理、月之国是带tRNS色键的RGB PNG，其余为RGBA；缩小时自然得到平滑边缘。

## 许可

《超级马里奥 奥德赛》的美术与标识属于Nintendo及相应权利人，这些硬币仅作个人电视主题使用，不是Nintendo官方主题。权利说明与角色素材相同，见[../mario-odyssey-icons/LICENSE-ASSETS.md](../mario-odyssey-icons/LICENSE-ASSETS.md)。build_coins.py沿用项目代码许可。

## 重建

只用Python标准库，另需ffmpeg/ffprobe与libwebp的 `cwebp`/`dwebp`（`brew install ffmpeg webp`；优先使用 `/opt/homebrew/bin` 下的版本，其次PATH）。

```sh
python3 -I build_coins.py            # 写入 ../../theme-packs/mario-odyssey/icons/
python3 -I build_coins.py /tmp/coins # 先写到别的目录检查
```

每枚硬币的处理：

1. 按sources.json核对原图的PNG签名、尺寸和SHA-256，原图有改动即停止。
2. 裁掉四周完全透明的边（现有原图已贴边，裁切为空操作）。
3. 用ffmpeg在预乘alpha下以Lanczos缩小，避免原图透明区里的灰色渗进边缘；长边缩到124 px，保持比例，居中放进128×128透明画布（四周至少留2 px）。
4. 把邻近可见像素的颜色逐圈填进完全透明的像素，alpha不变。有损WebP的色度只有一半分辨率，不这样做硬币边缘会被平均得发黑。
5. `cwebp -q 90 -alpha_q 100 -alpha_filter best -m 6 -sharp_yuv -exact` 编码：颜色有损，alpha无损，`-exact` 保留第4步的颜色。
6. 用 `dwebp` 解码回来检查：128×128、VP8X+ALPH+VP8结构、alpha与编码前逐字节一致、硬币居中且长边不小于122 px。

导出的7张（`PACKED`，即除都市外的王国）全部在临时目录通过后，才逐个以点文件暂存并改名为 `coin-<id>.webp`，主题包目录里不会留下写了一半的文件；最后写validation/coins-report.json。输出是确定的，重复运行字节不变（2026-10-23复查：7张与主题包中的文件逐字节一致）。

## 显示尺寸

在freeguy（1920×1080、320 dpi，1dp = 2px）上硬币按46dp画成92×92 px，由128×128缩小，细节足够。这些渲染图没有矢量金币（`ic_odyssey_coin.xml`）那样的深色描边，在深色壁纸上紫色很醒目；若放在浅色背景上需要更强的轮廓，可在绘制时加阴影，不必改原图。
