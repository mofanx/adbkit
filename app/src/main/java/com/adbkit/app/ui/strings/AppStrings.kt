package com.adbkit.app.ui.strings

import androidx.compose.runtime.compositionLocalOf

val LocalStrings = compositionLocalOf<AppStrings> { ZhStrings }

interface AppStrings {
    val langCode: String

    // Common
    val appName: String
    val appSubtitle: String
    val menu: String
    val settings: String
    val refresh: String
    val cancel: String
    val confirm: String
    val close: String
    val ok: String
    val retry: String
    val send: String
    val delete: String
    val search: String
    val more: String
    val copy: String
    val execute: String
    val connect: String
    val disconnect: String
    val disconnectConfirmTitle: String
    val disconnectConfirmMessage: String
    val loading: String
    val noData: String

    // Screen titles (drawer)
    val screenHome: String
    val screenDeviceInfo: String
    val screenTools: String
    val screenRemoteControl: String
    val screenFileManager: String
    val screenAppManager: String
    val screenProcessManager: String
    val screenTerminal: String
    val screenFastboot: String
    val screenSettings: String

    // Home screen
    val pair: String
    val fastboot: String
    val restartAdbService: String
    val disconnectAll: String
    val connectedDevices: (Int) -> String
    val wifiConnection: String
    val usbConnection: String
    val scan: String
    val wirelessPair: String
    val wirelessPairDesc: String
    val ipAddress: String
    val port: String
    val pairCode: String
    val restartingAdb: String
    val adbRestarted: String
    val restartFailed: (String) -> String
    val allDisconnected: String
    val pairingSuccess: String
    val pairingFailed: String
    val fillAllFields: String
    val connectingTo: (String) -> String
    val connected: String
    val connectFailed: (String) -> String
    val pleaseEnterIp: String

    // Device Info screen
    val deviceInfo: String
    val copyAll: String
    val gettingDeviceInfo: String
    val basicInfo: String
    val systemInfo: String
    val hardwareInfo: String
    val batteryInfo: String
    val networkInfo: String
    val otherInfo: String
    val moreInfo: String
    // Device info keys
    val diModel: String
    val diBrand: String
    val diDeviceName: String
    val diSerialNumber: String
    val diHardware: String
    val diAndroidVersion: String
    val diSdkVersion: String
    val diBuildId: String
    val diSecurityPatch: String
    val diBasebandVersion: String
    val diKernelVersion: String
    val diCpuArch: String
    val diScreenResolution: String
    val diScreenDensity: String
    val diTotalMemory: String
    val diAvailableMemory: String
    val diTotalStorage: String
    val diAvailableStorage: String
    val diBatteryLevel: String
    val diBatteryStatus: String
    val diBatteryTemperature: String
    val diIpAddress: String
    val diWifiMac: String
    val diUptime: String

    // Tools screen
    val tools: String
    val toolScreenshot: String
    val toolScreenRecord: String
    val toolInstallApk: String
    val toolRebootDevice: String
    val toolInputText: String
    val toolKeyEvent: String
    val toolBrightness: String
    val toolScreenTimeout: String
    val toolWifiToggle: String
    val toolBluetoothToggle: String
    val toolAirplaneMode: String
    val toolOpenUrl: String
    val toolLaunchApp: String
    val toolCurrentActivity: String
    val toolLogcat: String
    val toolSystemProperties: String
    val toolScreenDensity: String
    val toolScreenResolution: String
    val toolNavBar: String
    val toolStatusBar: String
    val toolDescScreenshot: String
    val toolDescScreenRecord: String
    val toolDescInstallApk: String
    val toolDescRebootDevice: String
    val toolDescInputText: String
    val toolDescKeyEvent: String
    val toolDescBrightness: String
    val toolDescScreenTimeout: String
    val toolDescWifiToggle: String
    val toolDescBluetoothToggle: String
    val toolDescAirplaneMode: String
    val toolDescOpenUrl: String
    val toolDescLaunchApp: String
    val toolDescCurrentActivity: String
    val toolDescLogcat: String
    val toolDescSystemProperties: String
    val toolDescScreenDensity: String
    val toolDescScreenResolution: String
    val toolDescNavBar: String
    val toolDescStatusBar: String
    // Reboot dialog
    val rebootDevice: String
    val normalReboot: String
    val rebootToRecovery: String
    val rebootToBootloader: String
    val rebootToFastboot: String
    val rebootToEdl: String
    // Input text dialog
    val inputText: String
    val textContent: String
    // Key event dialog
    val keyEvent: String
    val keyHome: String
    val keyBack: String
    val keyVolUp: String
    val keyVolDown: String
    val keyPower: String
    val keyMenu: String
    val keyRecent: String
    val keyMute: String
    val keySleep: String
    val keyWake: String
    val keyTab: String
    val keyEnter: String
    val keyBackspace: String
    val keyEsc: String
    val keyScreenshot: String
    // Brightness dialog
    val screenBrightness: String
    val brightnessValue: (Int) -> String
    val set: String
    // Open URL dialog
    val openUrl: String
    // Launch app dialog
    val launchApp: String
    val packageName: String
    val launch: String
    // Density dialog
    val modifyDensity: String
    val commonDpi: String
    val dpiValue: String
    val resetDefault: String
    // Resolution dialog
    val modifyResolution: String
    val resolutionFormat: String
    val resolution: String
    // Logcat dialog
    val noLog: String
    val clear: String

    // App Manager screen
    val appManager: String
    val userApps: (Int) -> String
    val systemApps: (Int) -> String
    val searchPackage: String
    val loadingAppList: String
    val appLaunch: String
    val appForceStop: String
    val appClearData: String
    val appUninstall: String
    val appFreeze: String
    val appEnable: String
    val appBackupApk: String
    val appDetails: String
    val action: String
    val adVersionName: String
    val adVersionCode: String
    val adInstallTime: String
    val adUpdateTime: String
    val adApkPath: String
    val adDataDir: String
    val adTargetSdk: String
    val adMinSdk: String

    // File Manager screen
    val fileManager: String
    val newFolder: String
    val homeDir: String
    val parentDir: String
    val sdCard: String
    val download: String
    val album: String
    val data: String
    val rootDir: String
    val emptyDir: String
    val folderName: String
    val create: String
    val downloadToLocal: String

    // Process Manager screen
    val processManager: String
    val searchProcess: String
    val totalProcesses: (Int) -> String
    val pid: String
    val memory: String
    val processName: String
    val killProcess: String

    // Terminal screen
    val terminal: String
    val clearScreen: String
    val switchMode: String
    val adbShellMode: String
    val adbCommandMode: String
    val notConnected: String
    val enterCommand: String
    val history: String
    val switchedToShellMode: String
    val switchedToCommandMode: String
    val cleared: String
    val commandSuccess: String
    val commandFailed: String

    // Fastboot screen
    val fastbootDeviceConnected: String
    val fastbootInsertDevice: String
    val flashImage: String
    val imageFilePath: String
    val selectFile: String
    val partition: String
    val flash: String
    val quickActions: String
    val rebootSystem: String
    val rebootBl: String
    val rebootRecovery: String
    val rebootFastbootd: String
    val unlockBl: String
    val lockBl: String
    val erasePartition: String
    val deviceVariables: String
    val customCommand: String
    val commandOutput: String
    val detectDevice: String

    // Remote Control screen
    val remoteControl: String
    val fullscreen: String
    val gettingScreen: String
    val longPressToggleControls: String
    val fpsOverlay: (Int) -> String
    val refreshRate: String
    val refreshRateLow: String
    val refreshRateMid: String
    val refreshRateHigh: String
    val refreshRateUltra: String
    val maxFps: String
    val aspectRatio: String
    val keepOriginal: String
    val adaptive: String
    val navBarPosition: String
    val floating: String
    val bottom: String
    val hidden: String
    val fullscreenDisplay: String
    val screenOffControl: String
    val audioTransmission: String
    val viewOnlyMode: String
    val usageGuide: String
    val usageTip1: String
    val usageTip2: String
    val usageTip3: String
    val usageTip4: String
    val usageTip5: String
    val btnBack: String
    val btnHome: String
    val btnRecent: String

    // Settings screen
    val adbConfig: String
    val adbPath: String
    val adbPathHint: String
    val detect: String
    val autoDetect: String
    val fastbootPath: String
    val defaultPort: String
    val connectionSettings: String
    val autoConnectLastDevice: String
    val keepScreenOn: String
    val saveCommandHistory: String
    val safetySettings: String
    val confirmDangerous: String
    val confirmDangerousDesc: String
    val appearanceSettings: String
    val darkMode: String
    val darkModeSystem: String
    val darkModeLight: String
    val darkModeDark: String
    val dynamicColor: String
    val dynamicColorDesc: String
    val languageSettings: String
    val language: String
    val chinese: String
    val english: String
    val about: String
    val aboutAppName: String
    val aboutVersion: String
    val aboutDeveloper: String
    val aboutRepo: String
    // ADB status messages
    val adbStatusReady: String
    val adbStatusNotReady: String
    val adbDiagnostics: String
    val detecting: String
    val autoDetecting: String
    val adbAvailable: (String) -> String
    val adbUnavailable: (String) -> String
    val adbFound: (String, String) -> String
    val adbNotFound: String

    // History & Scan
    val noHistory: String
    val scanningLan: String
    val scanResult: (Int) -> String
    val noDevicesFound: String
    val scanLan: String

    // Device click target settings
    val deviceClickTarget: String
    val deviceClickTargetDesc: String

    // Sidebar device info
    val currentDevice: String
    val noDeviceConnected: String

    // File management
    val upload: String
    val uploadFile: String
    val downloading: String
    val uploading: String
    val downloadSuccess: String
    val uploadSuccess: String
    val downloadFailed: String
    val uploadFailed: String

    // App management
    val installApk: String
    val selectApk: String
    val installing: String
    val installSuccess: String
    val installFailed: String

    // Process management
    val memoryUsage: String
    val totalMemory: String
    val usedMemory: String
    val freeMemory: String
    val processKilled: String
    val runningApps: String
    val allProcesses: String
    val runningAppsCount: (Int) -> String

    // Remote control - bitrate
    val bitrateControl: String

    // Storage info
    val storageInfo: String
    val internalStorage: String
    val storageUsed: String
    val storageFree: String
    val storageTotal: String
}
