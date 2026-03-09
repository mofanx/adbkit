# ADB Kit - 专业ADB助手

一款功能强大的 Android ADB 助手应用，支持通过 WiFi 或 USB 连接管理 Android 设备。

## 功能特性

### 🔗 设备连接
- WiFi ADB 连接 (支持 IP:Port)
- Android 11+ 无线调试配对
- 多设备管理与切换
- 网络设备扫描

### ℹ️ 设备信息
- 完整硬件/软件/网络信息
- 电池状态监控
- 存储空间查看
- 一键复制所有信息

### 🛠️ 实用工具
- 截图/录屏
- 安装APK
- 多模式重启 (系统/Recovery/Bootloader/Fastboot/EDL)
- 输入文本/按键模拟
- 屏幕亮度/超时调节
- WiFi/蓝牙/飞行模式开关
- 打开链接/启动应用
- 查看当前Activity
- Logcat日志查看
- 系统属性查看
- 屏幕密度/分辨率修改
- 导航栏/状态栏控制

### 🖱️ 远程控制
- 虚拟按键控制 (方向/导航/音量/电源)
- 分辨率/码率/画面比例设置
- 熄屏控制
- 兼容模式

### 📁 文件管理
- 浏览设备文件系统
- 快捷目录导航 (SD卡/下载/相册/数据/根目录)
- 新建文件夹
- 删除文件/目录
- 下载文件到本地

### 📱 应用管理
- 用户应用/系统应用分类查看
- 搜索应用
- 启动/强制停止应用
- 卸载/清除数据
- 冻结(禁用)/启用应用
- 备份APK
- 查看应用详细信息

### ⚙️ 进程管理
- 实时进程列表
- 按名称/PID搜索
- 内存占用显示
- 结束进程

### 💻 运行命令
- ADB Shell 模式
- ADB 命令模式
- 命令历史记录
- 快捷命令
- 彩色终端输出

### ⚡ Fastboot
- 刷入镜像 (多分区支持)
- 快捷操作 (重启/解锁BL/锁定BL/擦除分区)
- 自定义 Fastboot 命令
- 设备变量查看
- 命令行输出

### ⚙️ 设置
- ADB/Fastboot 路径配置
- 连接设置
- 安全设置 (危险操作确认)
- 外观设置 (深色模式/动态颜色)

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **架构**: MVVM
- **最低支持**: Android 8.0 (API 26)
- **目标版本**: Android 15 (API 35)

## 构建

### 1. 准备 ADB 二进制文件

应用需要内置 ADB 可执行文件才能在非 root 设备上正常工作。ADB 二进制必须是为 **Android 架构** (ARM64/ARM/x86_64) 编译的，而非桌面 Linux 版本。

```bash
# 方式一：设置下载 URL（推荐用于 CI）
ADB_DOWNLOAD_URL=https://your-server/adb-binaries ./scripts/download_adb_binaries.sh

# 方式二：从 GitHub Release 下载
ADB_GITHUB_REPO=user/repo ADB_RELEASE_TAG=v1.0 ./scripts/download_adb_binaries.sh

# 方式三：仅 arm64（大多数现代设备）
./scripts/download_adb_binaries.sh --arm64-only

# 方式四：手动放置
# 将编译好的 adb 放入以下目录：
#   app/src/main/assets/bin/arm64-v8a/adb
#   app/src/main/assets/bin/armeabi-v7a/adb
#   app/src/main/assets/bin/x86_64/adb
```

**获取 Android 原生 ADB 二进制的途径：**
- 在 Termux 中执行 `pkg install android-tools`，然后复制 `$(which adb)`
- 从 AOSP 源码使用 NDK 编译静态链接版本
- 从可信的第三方预编译项目获取

### 2. 构建 APK

项目使用 GitHub Actions 自动构建。推送到 `main` 分支即可触发构建。

### GitHub Secrets 配置

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | Base64 编码的签名密钥文件 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |
| `ADB_DOWNLOAD_URL` | (可选) ADB 二进制下载 URL |
| `ADB_GITHUB_REPO` | (可选) 存放 ADB 二进制的 GitHub 仓库 |
| `ADB_RELEASE_TAG` | (可选) GitHub Release 标签 |

## 许可证

MIT License
