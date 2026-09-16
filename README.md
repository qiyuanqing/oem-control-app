# O0AI 设备管控软件

这是一个面向 Android 8.1 的 Java 原型工程，供系统集成和 GitHub Actions 编译使用。`minSdk` 为 27，`targetSdk` 为 34：设备最低支持 Android 8.1，同时通过当前 Android SDK 的发布检查。

软件没有 `MAIN/LAUNCHER` 图标入口，不会作为普通应用显示在桌面；它只声明了无图标的 HOME/Kiosk 入口。开发阶段可以通过 ADB 打开管理页面：

```bat
adb shell am broadcast -a com.o0ai.control.action.DEBUG_STATUS
adb shell am broadcast -a com.o0ai.control.action.OPEN_ADMIN
```

正式版本不公开管理 Activity。`OPEN_ADMIN` 只能通过声明的系统 `DUMP` 权限调用，工程版由 ADB shell 使用。

## 功能

- `DeviceAdminReceiver` 和 Device Owner 兼容实现；
- 固定应用白名单和 LockTask/Kiosk 策略；
- 禁止安装、卸载、恢复出厂、安全模式和添加用户等设备限制；
- 可选禁用相机、禁止截屏、禁用状态栏，默认不影响甲方业务硬件；
- 中文管理员页面；
- 按应用授予或拒绝 Android 运行时权限；
- 特殊设置页面入口由管理员密码保护；
- 完全授权模式默认关闭，只能在设备页面输入正确密码后手动开启；
- USB ADB 调试命令可携带密码；
- 临时调试授权默认 1 小时，完全授权模式下永久授权直到关闭指令；
- 重启后自动恢复策略；
- 表冠不由本软件修改，继续使用 `BUTTON_1 / KEYCODE_BUTTON_1 = 188`。

这是一个可编译的 APK 原型，不是已经刷入设备的成品系统镜像。以下能力仍需要厂商 Android 8.1 源码配合：SetupWizard 自动绑定 Device Owner、平台签名、专用 SELinux 域、直接控制系统签名权限，以及对特殊访问权限的无界面授权。

## 首次开发测试

先安装 APK，再在未配置设备或恢复出厂后的首次配置阶段执行：

```bat
adb install app-debug.apk
adb shell dpm set-device-owner com.o0ai.control/.admin.ControlAdminReceiver
```

开发阶段使用下面的受控广播入口打开管理页：

```bat
adb shell am broadcast -a com.o0ai.control.action.OPEN_ADMIN
```

`dpm set-device-owner` 只用于开发测试。量产设备需要修改 SetupWizard/ManagedProvisioning，使系统在首次配置时自动绑定该组件，具体方案见工作区的 `OEM_DEVICE_CONTROL_DEBUG_SPEC.md`。

## ADB 调试接口

下面的密码示例仅说明命令格式，实际密码由设备首次打开管理页面时设置：

```bat
adb shell am broadcast -a com.o0ai.control.action.DEBUG_STATUS
adb shell am broadcast -a com.o0ai.control.action.OPEN_ADMIN
adb shell am broadcast -a com.o0ai.control.action.DEBUG_UNLOCK --es password "工程密码"
adb shell am broadcast -a com.o0ai.control.action.DEBUG_UNLOCK --es password "工程密码" --ez permanent true
adb shell am broadcast -a com.o0ai.control.action.OPEN_SETTINGS --es password "工程密码"
adb shell am broadcast -a com.o0ai.control.action.POLICY_RESTORE --es password "工程密码"
adb shell am broadcast -a com.o0ai.control.action.DEBUG_LOCK --es password "工程密码"
```

行为约定：

- `DEBUG_UNLOCK` 为临时授权；
- `--ez permanent true` 只有设备管控软件中已手动开启完全授权模式时才接受；
- 完全授权模式默认关闭，ADB 不能直接打开；
- `OPEN_SETTINGS` 会先验证密码，再临时开放设置；
- 任何错误都保持管控；
- `POLICY_RESTORE`/`DEBUG_LOCK` 恢复策略，但不会删除 Device Owner。

## GitHub Actions

工程自带 `.github/workflows/android.yml`。推送到 GitHub 后会使用 JDK 17、Android SDK 35 和 Build Tools 构建 Debug/Release APK，并上传构建产物。

请将本目录 `oem-control-app` 的内容作为 GitHub 仓库根目录上传，使工作流位于：

```text
.github/workflows/android.yml
settings.gradle
app/build.gradle
```

不要把它作为更大目录中的普通子目录上传，否则 GitHub 不会识别嵌套的 `.github/workflows`。

## 系统集成注意事项

APK 预装到 `/system/priv-app` 不会自动成为 Device Owner。量产必须将它接入 SetupWizard/ManagedProvisioning。正式固件还应使用平台签名、专用 SELinux 策略和受控 USB 调试配置。

`DeviceAdminReceiver` 只声明 Android 公开的设备管理员能力；将 APK 放入 `/system/priv-app` 不会自动增加 Device Owner 权限。系统源码集成时，必须使用同一套平台签名和包名 `com.o0ai.control`，并在首次配置阶段通过 ManagedProvisioning 完成绑定。

本工程是可编译原型，不包含厂商 Android 源码中的 SetupWizard、ManagedProvisioning 或 `system_server` 修改；这些需要根据设备的 Android 8.1 源码树单独集成。
