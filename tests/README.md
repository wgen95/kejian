# 逻辑和读取检查

这些检查不等同于安卓设备上的浏览器界面或真实账号登录流程验收。

## 读取器

```powershell
node tests/extractor.test.cjs
```

24 项：单双周、不连续周次、重复、星期日、嵌套原始排课、教师教室、未知格式、错误域名、学期元数据、未排课提示、敏感字段排除，以及明确学期编号不一致时拒绝读取。

## 课程逻辑

```powershell
javac -encoding UTF-8 --release 8 -d build/tests app/src/main/java/cn/kejian/timetable/Course.java tests/CourseTest.java
java -cp build/tests CourseTest
```

23 项：周次、冲突规则、跨年和学期边界、编辑拷贝隔离。

## 保存、解码和数据校验

使用 SDK 36 的 `android.jar` 作为编译依赖，以及仅测试使用的 `org.json:json:20250107` JAR。APK 使用安卓系统 JSON，不打包测试 JAR。

编译 `Course.java`、`Store.java`、`ImportPayload.java` 和对应测试，运行：

- `cn.kejian.timetable.StoreTest`：23 项。内存 SharedPreferences 替身检查保存重开、备份往返、非法数字/作息、日期、过短学期、失败写入和原始损坏数据保留。
- `cn.kejian.timetable.ImportPayloadTest`：18 项。检查格式、大小、课程、学期信息、空数据及 WebView 字符串回调的双层 JSON 解码。

## 浏览器规则

编译 `BrowserPolicy.java` 和 `tests/BrowserPolicyTest.java`，运行 `cn.kejian.timetable.BrowserPolicyTest`。

746 项检查网址补全、普通 HTTPS 导航与准确读取范围，以及两条已报告 GET 回跳的修正。额外检查原始转义、加号、重复参数和片段完整保留；只处理已知主机、端口、路径与后缀；POST、旁站、编码前缀、未知后缀不改写，正常回调不重复处理。

在桌面 JVM 运行涉及 JSON 的测试时，把测试 JSON JAR 放在 SDK JAR 前面：`build/tests;.tools/json-test.jar;.tools/platform36/android-36/android.jar`。SDK JAR 中的 JSON 类是编译用存根，不能作为测试运行实现。

## 页面读取检查

`node tests/make-browser-fixture.cjs` 生成 `build/browser-test/fixture.html` 和 Playwright CLI 用的 `open-fixture.cjs`。后者拦截同济课表 URL，返回合成课表页面，不进行学校登录。

点击合成页面的「导课」按钮会执行与应用相同的读取脚本，并将返回值保存为 WebView 回调相同形式的序列化字符串。已在桌面 Chromium 检查课程、教室、单双周和学期正确。

另外已在官方 Android 16 / API 36 Google ATD、WebView 133.0.6943.137 上，对非 debuggable 的 0.3.2 APK 完成原生按钮→匿名 IAM 登录入口检查，以及合成课程读取→原生预览→教师编辑字段可见→取消后课程未变检查。独立测试包的源码、构建脚本、实测证据和可重跑命令见 [native-runtime/README.md](native-runtime/README.md)。真实账号认证、真实课表结构、不同手机、横屏和系统返回键仍需实际验证。

总计 834 项逻辑检查；另做了桌面浏览器中的读取验证、真实学校登录入口检查，以及原生客户端的回跳修正验证。原生修正场景阻止网络加载，模拟参数不会发往学校，不作为真实账号登录成功的证明。
