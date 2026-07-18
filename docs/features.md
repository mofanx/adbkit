# ADB Kit 功能文档

> 版本：v1.4.0（versionCode 7）  
> 适用代码库：`app/src/main/java/com/adbkit/app`  
> 最后更新：基于当前 main 分支源码整理

## 1. 产品定位

ADB Kit 是一款面向 Android 开发者、测试工程师和极客用户的移动端 ADB 助手。它让手机/平板无需连接电脑即可通过 ADB/Fastboot 管理其他 Android 设备，支持 WiFi/USB 连接、远程投屏控制、文件/应用/进程管理、命令终端、Fastboot 刷机等能力。

## 2. 技术栈与运行环境

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material Design 3
- **架构模式**：MVVM（StateFlow + ViewModel + Compose UI）
- **最低系统**：Android 8.0（API 26）
- **目标系统**：Android 15（API 35）
- **ADB 二进制**：以内置 `libadb.so` / `libfastboot.so` 形式打包在 `jniLibs/`；运行时被解压到 `nativeLibraryDir`（可执行）
- **屏幕串流**：自研 `ScreenServer.java` DEX，通过 `app_process` 在目标设备运行，输出 scrcpy 兼容的 H.264 帧协议

## 3. 核心功能模块

### 3.1 设备连接（Home）

- **IP:Port 连接**：输入目标设备 IP，默认端口 5555，支持自定义端口
- **连接历史**：下拉选择/删除历史连接地址
- **网络扫描**：一键扫描局域网 5555 端口开放设备
- **无线调试配对**：支持 Android 11+ 的 `adb pair` 配对流程（IP、端口、配对码）
- **多设备管理**：列出当前所有 ADB 在线设备，点击切换当前设备
- **ADB 服务控制**：重启 ADB Server、断开所有设备
- **USB 连接**：自动识别通过 USB 连接的设备

### 3.2 设备信息（Device Info）

聚合展示目标设备的硬件/软件/网络/电池/存储信息，分为：

- **基本信息**：型号、品牌、设备名、序列号、硬件
- **系统信息**：Android 版本、SDK 版本、Build ID、安全补丁、基带版本、内核版本
- **硬件信息**：CPU 架构、屏幕分辨率、屏幕密度、总内存、可用内存、总存储、可用存储
- **电池信息**：电量、状态、温度
- **网络信息**：IP 地址、WiFi MAC
- **其他**：运行时间
- **操作**：一键复制全部信息、刷新

### 3.3 实用工具（Tools）

20 项网格化快捷工具：

| 工具 | 说明 |
|------|------|
| 截图 | `screencap` 截取设备屏幕并保存到 `/sdcard/screenshot_adbkit.png` |
| 录屏 | `screenrecord --time-limit 180` 后台录屏到 `/sdcard/screenrecord_adbkit.mp4` |
| 安装 APK | 调起系统文件选择器，将 APK push 到设备后 `pm install` |
| 重启设备 | 弹窗选择：正常/Recovery/Bootloader/Fastboot/EDL(9008) |
| 输入文本 | 弹窗输入文字，`input text` 发送到设备 |
| 按键模拟 | 弹窗选择 Home/Back/电源/音量/Menu/Recent/Tab/Enter 等 14 个常用 keyevent |
| 屏幕亮度 | 滑块调节 `settings put system screen_brightness` |
| 屏幕超时 | 一键设置为 5 分钟 |
| WiFi 开关 | 检测当前状态并切换 |
| 蓝牙开关 | `svc bluetooth enable/disable` |
| 飞行模式 | `settings put global airplane_mode_on` + 广播 |
| 打开链接 | 弹窗输入 URL，设备端用 `am start -a VIEW -d` 打开 |
| 启动应用 | 弹窗输入包名，`monkey -p` 启动 |
| 当前 Activity | `dumpsys activity top \| grep ACTIVITY` |
| Logcat | 抓取最近 200 条日志，弹窗查看，支持清除 |
| 系统属性 | `getprop` 全部属性查看 |
| 屏幕密度 | 设置/重置 `wm density` |
| 屏幕分辨率 | 设置/重置 `wm size` |
| 导航栏 | `immersive.navigation=*` 隐藏导航栏 |
| 状态栏 | 隐藏状态栏 |

### 3.4 远程控制（Remote Control）

实现类 scrcpy 的远程投屏与控制：

- **连接模式**：选择最大尺寸（720p~2K）、码率（2~32Mbps）、保持宽高比、熄屏、仅浏览、音频开关
- **导航栏样式**：悬浮/底部/隐藏
- **画面渲染**：H.264 硬解到 SurfaceView，支持 FPS 与分辨率叠加显示
- **触控交互**：
  - 单击 → `input tap`
  - 长按 → 同坐标 swipe 长按时长
  - 拖动 → `input swipe`
  - 底部/悬浮虚拟按键：Home、Back、Recent、音量+/-、电源
- **全屏**：可隐藏顶部控制栏
- **协议**：自研 `ScreenServer` 输出 8 字节视频头 + 12 字节帧元数据 + H.264/AAC 数据包

### 3.5 文件管理（File Manager）

- **目录浏览**：`ls -la` 解析，目录在前、按名称排序
- **快捷导航**：SD卡、Download、DCIM、/data、根目录、上级目录、主目录
- **文件操作**：进入目录、下载到本地（`adb pull`）、删除（`rm -rf`）
- **新建文件夹**：`mkdir -p`
- **上传文件**：系统文件选择器 → push 到当前目录
- **传输状态**：进度/成功/失败提示

### 3.6 应用管理（App Manager）

- **应用分类**：用户应用 / 系统应用 Tab 切换
- **搜索过滤**：包名实时搜索
- **应用操作**：启动、强制停止、卸载、清除数据、冻结/禁用、启用、备份 APK
- **应用详情**：版本名、版本号、安装/更新时间、APK 路径、数据目录、目标 SDK、最低 SDK
- **安装 APK**：选择本地 APK → push 到 `/data/local/tmp` → `pm install`

### 3.7 进程管理（Process Manager）

- **视图切换**：运行中的应用 / 所有进程
- **内存概览**：总内存、已用、可用、使用百分比进度条
- **进程列表**：PID、用户、内存、CPU、进程名
- **搜索**：按名称/PID 过滤
- **操作**：结束进程（`kill -9`）、强制停止应用

### 3.8 运行命令（Terminal）

- **双模式**：
  - ADB Shell 模式：`adb shell <command>`
  - ADB 命令模式：`adb <command>`
- **快捷命令**：Shell 模式提供 `ls`、`pwd`、`ps`、`df`、`top -n 1`、`getprop`；命令模式提供 `devices`、`version`、`get-state` 等
- **命令历史**：可查看、点击复用
- **彩色输出**：命令前缀、错误、分隔符高亮
- **清屏**：一键清空终端输出

### 3.9 Fastboot（Fastboot）

- **设备检测**：`fastboot devices` 自动检测
- **刷入镜像**：选择分区（recovery/boot/system/vendor/dtbo/vbmeta/cache/userdata/super）并输入镜像路径刷入
- **快捷重启**：重启系统、重启 Bootloader、重启 Recovery、重启 Fastbootd
- **Bootloader 锁**：解锁/锁定 Bootloader（`flashing unlock/lock`）
- **擦除分区**：`fastboot erase`
- **查看变量**：`fastboot getvar all`
- **自定义命令**：输入任意 fastboot 子命令执行

### 3.10 设置（Settings）

- **ADB 配置**：
  - 手动指定 ADB/Fastboot 路径
  - 检测 ADB 可用性
  - 自动检测候选路径（jniLibs、系统路径）
  - ADB 诊断信息展示
- **连接设置**：默认端口、自动连接上次设备、保持亮屏、保存命令历史
- **安全设置**：危险操作确认开关
- **外观设置**：深色模式（跟随系统/开/关）、动态取色
- **语言**：中文/英文切换（通过 `AppStrings` 多语言抽象）

## 4. 数据持久化

- `SettingsRepository` 使用 Jetpack DataStore Preferences 保存：
  - ADB/Fastboot 路径
  - 默认端口
  - 自动连接、深色模式、动态取色、保持亮屏、危险确认、保存历史、语言
  - **连接历史**：最多 20 条，自动去重并按最近使用排序
  - **终端命令历史**：最多 50 条，可在设置中关闭保存

## 5. 安全与稳定性

- **命令参数 shell 转义**：`AdbService` 对文件路径、包名、URL、分区名等动态参数统一做 `shellQuote`，防止注入和引号错误
- **危险操作二次确认**：应用管理、文件管理、Fastboot 关键操作均受 `confirmDangerous` 开关控制
- **root 检测与提示**：文件管理器在访问 `/data`、`/system` 等受保护目录时，若未检测到 `su` root 权限，会显示提示横幅
- **命令取消**：终端执行中的命令支持取消按钮，底层 ADB/Fastboot 进程会被终止
- **命令收藏夹**：终端支持将常用命令加入收藏夹（最多 20 条，DataStore 持久化），可快速填充或移除
- **远程控制设备引导**：进入远程控制页面前检测当前连接设备，未连接时显示引导提示
- **统一空设备占位**：应用、文件、进程、设备信息、远程控制页面在无设备或错误时显示统一的 `EmptyDevicePlaceholder` 占位页（带重试按钮）

## 6. 关键服务与工具类

| 文件 | 职责 |
|------|------|
| `AdbService.kt` | ADB/Fastboot 命令封装、设备信息获取、文件/应用/进程 API、截图/按键等工具方法 |
| `AdbBinaryManager.kt` | 在 `nativeLibraryDir` 查找 `libadb.so`/`libfastboot.so`，校验可执行性，提供诊断信息 |
| `ScreenStreamService.kt` | 推送 `screen-server.dex`、启动 ScreenServer、H.264/AAC 解码并渲染到 Surface |
| `ScreenServer.java` | 目标设备端采集屏幕 + 音频，输出 scrcpy 兼容协议 |
| `SettingsRepository.kt` | DataStore 偏好设置读写 |
| `MainActivity.kt` | 运行时权限申请（存储管理权限） |

## 7. 权限清单

- `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`
- `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_EXTERNAL_STORAGE`
- `FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`、`WAKE_LOCK`
- `REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES`

## 8. 构建产物

- 输出 APK：`adbkit-v{versionName}-{buildType}.apk`
- Release 默认启用 R8 压缩与资源收缩
- DEX 产物：`server/src/ScreenServer.java` → `app/build/server/assets/screen-server.dex`
