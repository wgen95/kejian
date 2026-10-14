# Android 原生运行检查

## 0.3.3 回跳修复验证

2026-10-14，正式 0.3.3 APK 在同一 Android 16 / API 36 ATD、WebView 133.0.6943.137 中通过 `repair`、`login`、`fixture` 三个场景。摘要为 `validation-summary-0.3.3.json`；报告为 `research/native-api36-0.3.3/repair-test.txt`、`login-test.txt`、`fixture-test.txt`。

`repair` 保持应用实际 WebViewClient，模拟 16 项主页面请求。测试时仅开启 `setBlockNetworkLoads(true)`，不向学校发送合成 code/state。确认两个已知回跳实际转为准确 HTTPS 目标，原始 query/fragment 完整保留；POST、非目标网址、未知后缀不重发，重复目标停止。清空模拟页面后恢复网络设置。这只验证客户端修正语义，不代表真实账号认证成功。

`login` 与 `fixture` 仍使用正式配置检查匿名认证页和合成导入预览。首次连续场景执行时 fixture 测试进程异常退出，单独重启后复查全部通过；首次结果保存在 `fixture-test-first-run.txt`。未据此改动正式应用。

当前版本安装与检查命令（下文 0.3.2 部分为历史记录）：

```powershell
adb -s emulator-5556 install -r ./dist/Kejian-0.3.3.apk
adb -s emulator-5556 install -r -t ./build/native-test/Kejian-native-test.apk
adb -s emulator-5556 shell am instrument -w -r -e scenario repair cn.kejian.nativetest/cn.kejian.nativetest.BrowserSmokeRunner
adb -s emulator-5556 shell am force-stop cn.kejian.timetable
adb -s emulator-5556 shell am instrument -w -r -e scenario login cn.kejian.nativetest/cn.kejian.nativetest.BrowserSmokeRunner
adb -s emulator-5556 shell am force-stop cn.kejian.timetable
adb -s emulator-5556 shell am instrument -w -r -e scenario fixture cn.kejian.nativetest/cn.kejian.nativetest.BrowserSmokeRunner
```

这是独立的 Instrumentation 测试包，目标包为 `cn.kejian.timetable`，runner 为 `cn.kejian.nativetest.BrowserSmokeRunner`。测试通过真实 MainActivity、BrowserActivity 和系统 WebView 运行；不修改正式应用的 WebViewClient、网页设置或发布 APK。测试源码随源包保留，生成的测试 APK 只放在 `build/native-test/`，不放进 `dist/` 或源码 ZIP。

## 已完成的实测

环境：Google 官方 Android 16 / API 36 `google_atd/x86_64` ATD 系统镜像（revision 1），WebView `com.android.webview 133.0.6943.137`。目标为非 debuggable 的 `Kejian-0.3.2.apk`，SHA-256：

```text
4344A97FAB1986A6A19822FE444620B5F4960D8DD01E8BA71F9F9582719AEB7F
```

- 2026-10-13，`login` 通过：从原生「从教务系统导入」按钮打开内置浏览器，跟随学校跳转至 `https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain`，实际可见用户名框、密码框、登录按钮各 1 个。证据为 `research/native-api36-0.3.2/login-test.txt`、`login-visible.png` 和 `login-native-ui.xml`。
- 2026-10-14，`fixture` 通过：应用自身读取器提取两条合成课程，原生导入预览显示课程、教室和不连续周次；编辑弹窗实际可见「甲老师」教师输入框；取消编辑和导入预览后，保存的 `courses` 原始值保持完全相等。证据为 `research/native-api36-0.3.2/fixture-test-complete.txt`，其中 `nativeEditorTeacherVisible`、`fixturePreviewCancelLeavesDataUnchanged`、`fixtureCancelledWithoutSaving` 和 `passed` 均为 `true`。

此前 `fixture-test-final.txt` 保留了旧检查在教师字段处失败的结果。最终检查在最多 2 秒内等待原生弹窗完成布局，直接检查已附着、持有窗口焦点的真实 `EditText` 和可见区域，避免读取 ATD 的过期 accessibility 快照。最终 ATD 运行开启了绘制（`debug.hwui.drawing_enabled=1`）；诊断字段 `nativeEditorWindowObservations` 记录了上述条件。

`login` 只验证匿名导航到认证页面，不输入或提交账号密码。`fixture` 使用 `tests/make-browser-fixture.cjs` 生成的合成页面，并取消导入，不保存课程。这两项不代表真实账号认证、真实课表结构、验证码、多因素认证、不同手机/WebView 版本、横屏或系统返回键均已验收；这些仍需相应设备与账号的实际检查。

## 构建

从项目根目录运行，需要 Node.js、JDK 17+、SDK platform 36 和 build-tools 36.0.0。脚本优先接受参数或 `JAVA_HOME` / `ANDROID_HOME`，未指定 SDK 时使用项目现有 `.tools/platform36/` 和 `.tools/buildtools36/`；不会下载工具。

```powershell
./tests/native-runtime/build-harness.ps1
```

本机已验证的完整命令：

```powershell
./tests/native-runtime/build-harness.ps1 -JavaHome 'C:/Users/LENOVO/.codex/toolchains/cmfa-themes/jdk' -SdkRoot '' -NodeExecutable 'D:/Program Files/nodejs/node.exe'
```

可指定常规 SDK：

```powershell
./tests/native-runtime/build-harness.ps1 -JavaHome 'C:/path/to/jdk' -SdkRoot 'C:/path/to/Android/Sdk'
```

脚本读取 `.private/local-test.jks` 中 `kejian` alias 的现有本地测试密钥（密码 `android`）；不会创建或复制私钥。Android 要求测试包与目标应用签名匹配。源码 ZIP 不包含私钥，因此新环境需先用本地私有测试密钥重建目标应用，例如运行项目的 `build.ps1`，再构建测试包；不同密钥签署的测试包不能对现有发布 APK 执行 Instrumentation。

产物为 `build/native-test/Kejian-native-test.apk`，原生中间产物和资产也在 `build/native-test/`。现有合成页面生成器另写入 `build/browser-test/`。测试包需要 API 29+（使用 WindowInspector），本次实测仅限 API 36；正式应用的最低 API 要求不受影响。

## 安装与运行

在已启动的 Android 测试设备上执行，下例使用 `emulator-5556`。将 `adb` 替换为 SDK platform-tools 中的实际 adb 路径，并确认所选设备。

本机 adb 为 `C:/Users/LENOVO/.codex/toolchains/cmfa-themes/android-sdk/platform-tools/adb.exe`。本机已有 `KejianApi36` AVD 的启动方式如下；其他环境需先安装同一 API 36 ATD 镜像并创建自己的 AVD，镜像和模拟器不包含在源码 ZIP 中。

```powershell
$env:ANDROID_AVD_HOME = (Resolve-Path './.tools/android-runtime/avd').Path
$env:ANDROID_SDK_ROOT = 'C:/Users/LENOVO/.codex/toolchains/cmfa-themes/android-sdk'
Start-Process -WindowStyle Hidden -FilePath './.tools/android-runtime/emulator/emulator.exe' -ArgumentList '-avd KejianApi36 -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader'
```

等待 `adb -s emulator-5556 shell getprop sys.boot_completed` 返回 `1`，再执行下列命令。始终用 `-s` 指定测试设备，避免操作其他已连接手机。

```powershell
adb devices
adb -s emulator-5556 install -r ./dist/Kejian-0.3.2.apk
adb -s emulator-5556 install -r -t ./build/native-test/Kejian-native-test.apk
```

ATD 默认可能关闭绘制；在 ATD 上进行原生字段可见性检查前开启，并重新启动目标进程：

```powershell
adb -s emulator-5556 shell setprop debug.hwui.drawing_enabled 1
adb -s emulator-5556 shell am force-stop cn.kejian.timetable
```

运行两项场景：

```powershell
adb -s emulator-5556 shell am instrument -w -e scenario login -e timeoutSeconds 90 cn.kejian.nativetest/cn.kejian.nativetest.BrowserSmokeRunner
adb -s emulator-5556 shell am instrument -w -e scenario fixture cn.kejian.nativetest/cn.kejian.nativetest.BrowserSmokeRunner
```

检查输出中的 `NATIVE_BROWSER_REPORT` JSON 与 `passed`。`login` 还需 `reachedVisibleSchoolLogin=true`；`fixture` 还需 `nativeEditorTeacherVisible=true`、`fixturePreviewCancelLeavesDataUnchanged=true` 和 `fixtureCancelledWithoutSaving=true`。失败时保留 `stage`、`failureCode` 和窗口诊断，不把仅打开 Activity 或仅读取合成数据当作完整通过。

`fixture` 先等待 `about:blank` 清除校方 SPA 定时器，再用 `loadDataWithBaseURL` 装载合成页面；base URL 和 history URL 均为合法课表地址 `https://1.tongji.edu.cn/studentTimeTable`。随后等待真实原生读取按钮可用、加载进度为 100、进度条消失并稳定 1 秒，才点击应用自身按钮。测试不会替换 WebViewClient、修改 BrowserPolicy 或跳过应用的导入校验。
