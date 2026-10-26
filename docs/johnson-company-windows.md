# 公司电视桌面升级（Windows）

此流程用于升级已经安装本项目旧版 Launcher 的公司电视。公司构建内置并自动启用「乔山 · 品牌展厅」，主题菜单仅显示乔山，隐藏 ZIP 导入入口，保留七张品牌壁纸及轮播。无需另装 Python 或手工导入 ZIP；已有应用顺序、固定栏和手动设置的行列数会保留。首次启用乔山时会重置此前的壁纸选择，让品牌壁纸生效。升级时桌面会重启，但不会关闭电视。

请使用下面的专用脚本构建；仅切换分支后运行普通 Gradle 构建，不会启用公司配置。

## 准备

1. 使用当初编译并安装旧版 APK 的同一台 Windows 电脑、同一个 Windows 用户。必须保留原来的 `%USERPROFILE%\.android\debug.keystore`；不要删除、替换或重新生成。若原环境设置了 `ANDROID_USER_HOME` 或 `ANDROID_SDK_HOME`，脚本会停止，请先确认并恢复当初的原始签名环境。密钥遗失时停止，勿卸载电视上的应用。
2. 安装或沿用已配置的 Android Studio、JDK 和 Android SDK（含 platform-tools、build-tools），电视和电脑处于可互通的局域网，电视的网络调试已开启并授权。SDK 可通过 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或默认 `%LOCALAPPDATA%\Android\Sdk` 找到；JDK 可使用 `JAVA_HOME` 或 Android Studio 的 jbr。
3. 在新目录 clone 公司分支，避免覆盖原目录未提交的更改，然后在新仓库根目录打开 PowerShell：

   ```powershell
   git clone --depth 1 --branch dipsy/johnson-company https://github.com/DipsySu/cleanyourtv.git cleanyourtv-company
   cd cleanyourtv-company
   ```

   已有开发环境可以复用；新目录不意味着更换 Windows 用户或签名密钥。

## 升级

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\launcher\install-johnson.ps1 -TvHost 192.168.1.50
```

替换为电视的局域网 IP 或主机名；可使用 `IP:端口`，默认端口为 5555。脚本检测现装包名、构建更高版本、备份原 APK、比对签名后覆盖升级，并检查乔山主题已启用。备份保存在仓库的 `backup\johnson-upgrade` 唯一子目录中，请妥善保留。

若两个候选包都存在，脚本会停止；确认要升级哪一个之后，明确指定：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\launcher\install-johnson.ps1 -TvHost 192.168.1.50 -Package com.ts.launcher
```

也可指定 `com.gathin.tvhome`。若签名不一致、密钥丢失、设备未授权或验证失败，请保留备份并停止处理，不要卸载或清除数据。脚本只在升级导致服务丢失时恢复升级前已启用的本桌面按键无障碍服务，并保留升级后的其他服务；不会主动更改默认 HOME、其他应用、电源、音量、屏保或外置配置。

## 验证范围

2026-10-26，本地普通版和公司版均通过 252 项 JVM 测试、Android lint 和 release 构建。临时 Android 模拟器已验证从不支持 ZIP 的旧 APK 覆盖升级、配置保留、乔山自动启用、主题菜单仅显示乔山、隐藏导入入口，以及主题初始化短暂失败后的重试。

PowerShell 脚本已在 macOS 的 PowerShell 7 中完成语法解析和六种模拟场景检查：正常升级、签名不符、两个候选包并存、签名密钥丢失、默认 HOME 变化及原有按键服务恢复。尚未在公司 Windows 或公司小米电视上实测；安装脚本会在目标电视上再次检查签名、默认 HOME、原有按键服务和主题状态。
