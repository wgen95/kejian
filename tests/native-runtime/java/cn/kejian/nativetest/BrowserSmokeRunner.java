package cn.kejian.nativetest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only runner; it never submits credentials or saves imported courses. */
public final class BrowserSmokeRunner extends Instrumentation {
    private Bundle arguments;
    private Activity main;
    private Activity browser;
    private final JSONArray observations = new JSONArray();
    private final JSONObject report = new JSONObject();
    private long began;

    @Override public void onCreate(Bundle input) { arguments = input == null ? new Bundle() : input; start(); }

    @Override public void onStart() {
        int result = Activity.RESULT_CANCELED;
        began = SystemClock.elapsedRealtime();
        try {
            report.put("stage", "launch-main");
            report.put("scenario", arguments.getString("scenario", "login"));
            ApplicationInfo info = getTargetContext().getApplicationInfo();
            report.put("releaseDebuggable", (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
            PackageInfo pkg = getTargetContext().getPackageManager().getPackageInfo(info.packageName, 0);
            report.put("releaseVersion", pkg.versionName);
            report.put("androidSdk", android.os.Build.VERSION.SDK_INT);
            PackageInfo engine = WebView.getCurrentWebViewPackage();
            report.put("webViewProvider", engine == null ? "unknown" : engine.packageName + " " + engine.versionName);
            report.put("observations", observations);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("cn.kejian.timetable", "cn.kejian.timetable.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            main = startActivitySync(intent);
            waitForIdleSync();
            report.put("stage", "click-import-button");
            ActivityMonitor monitor = addMonitor("cn.kejian.timetable.BrowserActivity", null, false);
            checkedUi(() -> {
                View button = findText(main.getWindow().getDecorView(), "从教务系统导入", true);
                if (button == null) {
                    View settings = findText(main.getWindow().getDecorView(), "设置", false);
                    if (settings == null || !settings.performClick()) throw new IllegalStateException("settings_button_missing");
                    button = findText(main.getWindow().getDecorView(), "从教务系统导入", true);
                }
                if (button == null || !button.performClick()) throw new IllegalStateException("import_button_missing");
            });
            browser = waitForMonitorWithTimeout(monitor, 15000);
            removeMonitor(monitor);
            if (browser == null) throw new IllegalStateException("browser_activity_did_not_open");
            report.put("importButtonOpenedBrowser", true);
            report.put("stage", "browser-opened");
            if ("repair".equals(arguments.getString("scenario"))) {
                repair();
                report.put("passed", true);
                result = Activity.RESULT_OK;
            } else if ("fixture".equals(arguments.getString("scenario"))) {
                fixture();
                report.put("passed", true);
                result = Activity.RESULT_OK;
            } else {
                if (login()) { report.put("passed", true); result = Activity.RESULT_OK; }
                else report.put("passed", false);
            }
        } catch (Throwable failure) {
            try {
                report.put("passed", false); report.put("failureClass", failure.getClass().getName());
                String message = failure.getMessage();
                if (message != null && message.matches("[A-Za-z0-9_.-]{1,100}")) report.put("failureCode", message);
                if (browser != null) report.put("failureBrowserSnapshot", inspectBrowser());
            } catch (Exception ignored) { }
        } finally {
            try {
                runOnMainSync(() -> {
                    if (browser != null && !browser.isFinishing()) browser.finish();
                    if (main != null && !main.isFinishing()) main.finish();
                });
            } catch (Throwable ignored) { }
            Bundle output = new Bundle();
            output.putString("stream", "\nNATIVE_BROWSER_REPORT " + report.toString() + "\n");
            output.putString("report", report.toString());
            finish(result, output);
        }
    }

    /** Exercise the release client, with Chromium network loads blocked before synthetic parameters exist. */
    private void repair() throws Exception {
        AtomicReference<WebView> web = new AtomicReference<>();
        AtomicReference<WebViewClient> client = new AtomicReference<>();
        AtomicReference<Boolean> oldBlock = new AtomicReference<>();
        report.put("stage", "block-network-for-repair-semantics");
        report.put("repairIsSyntheticClientSemantics", true);
        report.put("accountLoginPerformed", false);
        report.put("syntheticParametersSentToSchool", false);
        checkedUi(() -> {
            web.set((WebView) field(browser, "web"));
            client.set(web.get().getWebViewClient());
            oldBlock.set(web.get().getSettings().getBlockNetworkLoads());
            web.get().stopLoading();
            web.get().getSettings().setBlockNetworkLoads(true);
        });
        try {
            blankWhileBlocked(web.get());
            report.put("syntheticNetworkLoadsBlocked", true);
            JSONArray checks = new JSONArray();
            report.put("repairChecks", checks);
            String query = "?code=NATIVE_TEST_DO_NOT_SEND%2B%2F%3D&state=TEST%26keep%3D%25&empty=&state=second+value#keep%2Fpart";
            String callback = "/api/ssoservice/system/loginIn";
            String suffix = ";https://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn";
            repairCheck(web.get(), client.get(), checks, "http_ssologin_get", "GET", true,
                    "http://1.tongji.edu.cn/ssologin" + query, true, "https://1.tongji.edu.cn/ssologin" + query);
            repairCheck(web.get(), client.get(), checks, "http_sslogin_default_port_get", "GET", true,
                    "http://1.tongji.edu.cn:80/sslogin" + query, true, "https://1.tongji.edu.cn/sslogin" + query);
            repairCheck(web.get(), client.get(), checks, "known_callback_suffix_get", "GET", true,
                    "https://1.tongji.edu.cn" + callback + suffix + query, true, "https://1.tongji.edu.cn" + callback + query);
            repairCheck(web.get(), client.get(), checks, "known_encoded_callback_suffix_get", "GET", true,
                    "https://1.tongji.edu.cn" + callback + "%3Bhttps%3A%2F%2F1-443.webvpn.tongji.edu.cn%2F%3Bhttps%3A%2F%2Fonetest.tongji.edu.cn" + query.replace("NATIVE_TEST_DO_NOT_SEND", "NATIVE_TEST_DO_NOT_SEND_ENCODED"),
                    true, "https://1.tongji.edu.cn" + callback + query.replace("NATIVE_TEST_DO_NOT_SEND", "NATIVE_TEST_DO_NOT_SEND_ENCODED"));
            repairCheck(web.get(), client.get(), checks, "duplicate_repair_stops_without_reissue", "GET", true,
                    "https://1.tongji.edu.cn" + callback + suffix + query, true, null);
            repairCheck(web.get(), client.get(), checks, "normal_https_callback_unchanged", "GET", true,
                    "https://1.tongji.edu.cn" + callback + query, false, null);
            repairCheck(web.get(), client.get(), checks, "http_post_blocked_without_reissue", "POST", true,
                    "http://1.tongji.edu.cn/ssologin" + query, true, null);
            repairCheck(web.get(), client.get(), checks, "https_callback_post_unchanged", "POST", true,
                    "https://1.tongji.edu.cn" + callback + suffix + query, false, null);
            repairCheck(web.get(), client.get(), checks, "other_http_host_not_upgraded", "GET", true,
                    "http://example.org/ssologin" + query, true, null);
            repairCheck(web.get(), client.get(), checks, "other_https_host_not_cleaned", "GET", true,
                    "https://example.org" + callback + suffix + query, false, null);
            repairCheck(web.get(), client.get(), checks, "forged_school_host_not_cleaned", "GET", true,
                    "https://1.tongji.edu.cn.example.org" + callback + suffix + query, false, null);
            repairCheck(web.get(), client.get(), checks, "unknown_suffix_unchanged", "GET", true,
                    "https://1.tongji.edu.cn" + callback + ";https://example.org" + query, false, null);
            repairCheck(web.get(), client.get(), checks, "nondefault_https_port_unchanged", "GET", true,
                    "https://1.tongji.edu.cn:8443" + callback + suffix + query, false, null);
            repairCheck(web.get(), client.get(), checks, "nondefault_http_port_not_upgraded", "GET", true,
                    "http://1.tongji.edu.cn:8080/ssologin" + query, true, null);
            repairCheck(web.get(), client.get(), checks, "subframe_not_reissued", "GET", false,
                    "http://1.tongji.edu.cn/ssologin" + query, false, null);
            repairCheck(web.get(), client.get(), checks, "encoded_prefix_unchanged", "GET", true,
                    "https://1.tongji.edu.cn/api/ssoservice/system/login%49n" + suffix + query, false, null);
            report.put("originalWebViewClientUsed", true);
            report.put("repairChecksPassed", checks.length());
        } finally {
            // Stop every blocked synthetic navigation and settle a local document before restoring connectivity.
            report.put("stage", "clear-synthetic-navigation-before-network-restore");
            blankWhileBlocked(web.get());
            checkedUi(() -> {
                if (web.get().getWebViewClient() != client.get()) throw new IllegalStateException("repair_client_replaced");
                web.get().getSettings().setBlockNetworkLoads(oldBlock.get());
                if (web.get().getSettings().getBlockNetworkLoads() != oldBlock.get())
                    throw new IllegalStateException("repair_network_setting_not_restored");
            });
            report.put("networkSettingRestored", true);
        }
        report.put("stage", "repair-semantics-complete");
    }

    private void blankWhileBlocked(WebView web) throws Exception {
        checkedUi(() -> {
            if (!web.getSettings().getBlockNetworkLoads()) throw new IllegalStateException("repair_network_not_blocked");
            web.stopLoading();
            web.loadUrl("about:blank");
        });
        for (int i = 0; i < 40; i++) {
            if ("true".equals(evaluate(web, "location.href==='about:blank'&&document.readyState==='complete'"))) return;
            SystemClock.sleep(100);
        }
        throw new IllegalStateException("repair_blank_not_settled");
    }

    private void repairCheck(WebView web, WebViewClient client, JSONArray checks, String label, String method,
            boolean mainFrame, String input, boolean expectedHandled, String expectedTarget) throws Exception {
        blankWhileBlocked(web);
        report.put("stage", "repair_" + label);
        AtomicReference<Boolean> handled = new AtomicReference<>();
        checkedUi(() -> {
            if (!web.getSettings().getBlockNetworkLoads()) throw new IllegalStateException("repair_network_not_blocked");
            if (web.getWebViewClient() != client) throw new IllegalStateException("repair_client_replaced");
            handled.set(client.shouldOverrideUrlLoading(web, new SyntheticRequest(input, method, mainFrame)));
        });
        if (handled.get() != expectedHandled) throw new IllegalStateException("repair_" + label + "_handled_wrong");
        AtomicReference<String> actual = new AtomicReference<>();
        AtomicReference<Boolean> unchangedClient = new AtomicReference<>();
        String target = expectedTarget == null ? "about:blank" : expectedTarget;
        for (int i = 0; i < 50; i++) {
            checkedUi(() -> { actual.set(web.getUrl()); unchangedClient.set(web.getWebViewClient() == client); });
            if (target.equals(actual.get())) break;
            SystemClock.sleep(100);
        }
        if (!target.equals(actual.get())) throw new IllegalStateException("repair_" + label + "_target_wrong");
        JSONObject check = new JSONObject();
        check.put("case", label);
        check.put("method", method);
        check.put("mainFrame", mainFrame);
        check.put("callbackHandled", handled.get());
        check.put("networkLoadsBlocked", true);
        check.put("originalClientUnchanged", unchangedClient.get());
        check.put("actualWebViewTargetMatches", true);
        check.put("reissued", expectedTarget != null);
        if (expectedTarget != null) {
            Uri uri = Uri.parse(expectedTarget);
            check.put("targetOriginAndPath", uri.getScheme() + "://" + uri.getHost() + uri.getEncodedPath());
            boolean rawPreserved = Uri.parse(input).getEncodedQuery().equals(uri.getEncodedQuery())
                    && Uri.parse(input).getEncodedFragment().equals(uri.getEncodedFragment());
            if (!rawPreserved) throw new IllegalStateException("repair_" + label + "_raw_query_changed");
            check.put("rawQueryAndFragmentPreserved", true);
        }
        checks.put(check);
    }

    private static final class SyntheticRequest implements WebResourceRequest {
        private final Uri uri;
        private final String method;
        private final boolean mainFrame;
        SyntheticRequest(String url, String method, boolean mainFrame) { this.uri = Uri.parse(url); this.method = method; this.mainFrame = mainFrame; }
        @Override public Uri getUrl() { return uri; }
        @Override public boolean isForMainFrame() { return mainFrame; }
        @Override public boolean isRedirect() { return true; }
        @Override public boolean hasGesture() { return false; }
        @Override public String getMethod() { return method; }
        @Override public Map<String, String> getRequestHeaders() { return Collections.emptyMap(); }
    }

    private boolean login() throws Exception {
        long deadline = SystemClock.elapsedRealtime() + Math.min(180, Math.max(20, Integer.parseInt(arguments.getString("timeoutSeconds", "90")))) * 1000L;
        while (SystemClock.elapsedRealtime() < deadline) {
            JSONObject observation = inspectBrowser();
            observations.put(observation);
            Bundle update = new Bundle();
            update.putString("stream", "\nNATIVE_BROWSER_OBSERVATION " + observation.toString() + "\n");
            sendStatus(0, update);
            JSONObject page = observation.optJSONObject("page");
            if (page != null && page.optString("location").equals("https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain")
                    && page.optInt("visiblePasswords") > 0 && page.optInt("visibleLoginButtons") > 0
                    && page.optInt("visibleUsernames") > 0) {
                report.put("reachedVisibleSchoolLogin", true);
                report.put("elapsedMs", SystemClock.elapsedRealtime() - began);
                return true;
            }
            SystemClock.sleep(2000);
        }
        report.put("reachedVisibleSchoolLogin", false);
        report.put("elapsedMs", SystemClock.elapsedRealtime() - began);
        return false;
    }

    private JSONObject inspectBrowser() throws Exception {
        JSONObject snapshot = new JSONObject();
        AtomicReference<WebView> web = new AtomicReference<>();
        AtomicReference<String> status = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<String> history = new AtomicReference<>();
        checkedUi(() -> {
            try {
                web.set((WebView) field(browser, "web"));
                status.set(((TextView) field(browser, "status")).getText().toString());
                failure.set(String.valueOf(field(browser, "lastFailure")));
                history.set(String.valueOf(field(browser, "navigationLog")));
            } catch (Exception ex) { throw safeFailure(ex); }
        });
        snapshot.put("elapsedMs", SystemClock.elapsedRealtime() - began);
        snapshot.put("status", status.get());
        snapshot.put("failure", failure.get());
        snapshot.put("sanitizedNativeHistory", history.get());
        if (web.get() == null) { snapshot.put("webViewMissing", true); return snapshot; }
        String script = "(function(){function visible(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}var inputs=[].slice.call(document.querySelectorAll('input'));var buttons=[].slice.call(document.querySelectorAll('button,input[type=submit],input[type=button],a'));return JSON.stringify({location:location.origin+location.pathname,readyState:document.readyState,visiblePasswords:inputs.filter(function(e){return e.type==='password'&&visible(e);}).length,visibleUsernames:inputs.filter(function(e){return (e.type==='text'||e.type==='email')&&visible(e);}).length,visibleLoginButtons:buttons.filter(function(e){return visible(e)&&(/登录|sign\\s*in|log\\s*in/i.test(e.textContent||'')||e.type==='submit');}).length,userAgent:navigator.userAgent});})()";
        String callback = evaluate(web.get(), script);
        String text = new JSONArray("[" + callback + "]").getString(0);
        snapshot.put("page", new JSONObject(text));
        return snapshot;
    }

    private void fixture() throws Exception {
        final String html;
        try (InputStream input = getContext().getAssets().open("fixture.html"); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
            html = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
        AtomicReference<WebView> web = new AtomicReference<>();
        String coursesBefore = getTargetContext().getSharedPreferences("kejian", 0).getString("courses", "<absent>");
        report.put("stage", "settle-fixture-browser");
        checkedUi(() -> {
            try {
                web.set((WebView) field(browser, "web"));
                web.get().stopLoading();
                // Clear pending school SPA timers before loading the independent synthetic document.
                web.get().loadUrl("about:blank");
            } catch (Exception ex) { throw safeFailure(ex); }
        });
        for (int i = 0; i < 20; i++) {
            if ("true".equals(evaluate(web.get(), "location.href==='about:blank'&&document.readyState==='complete'"))) break;
            SystemClock.sleep(250);
        }
        SystemClock.sleep(500);
        report.put("stage", "load-synthetic-fixture");
        checkedUi(() -> web.get().loadDataWithBaseURL("https://1.tongji.edu.cn/studentTimeTable", html, "text/html", "UTF-8", "https://1.tongji.edu.cn/studentTimeTable"));
        boolean ready = false;
        for (int i = 0; i < 30; i++) {
            String callback = evaluate(web.get(), "Boolean(document.getElementById('app')&&document.getElementById('app').__vue__&&document.readyState==='complete')");
            AtomicReference<Boolean> nativeReady = new AtomicReference<>(false);
            checkedUi(() -> nativeReady.set(((Button)field(browser,"importButton")).isEnabled()
                    && ((View)field(browser,"progress")).getVisibility() == View.GONE
                    && web.get().getProgress() == 100));
            if ("true".equals(callback) && nativeReady.get()) { ready = true; break; }
            SystemClock.sleep(500);
        }
        if (!ready) throw new IllegalStateException("fixture_not_ready");
        SystemClock.sleep(1000);
        report.put("fixtureIsSynthetic", true);
        report.put("stage", "click-native-extract");
        checkedUi(() -> {
            try {
                Button button = (Button) field(browser, "importButton");
                if (!button.isEnabled() || !button.performClick()) throw new IllegalStateException("fixture_import_button_disabled");
            } catch (Exception ex) { throw safeFailure(ex); }
        });
        AtomicReference<AlertDialog> preview = new AtomicReference<>();
        AtomicReference<String> pending = new AtomicReference<>();
        for (int i = 0; i < 30; i++) {
            checkedUi(() -> {
                try { preview.set((AlertDialog) field(main, "importDialog")); pending.set((String) field(main, "pendingImport")); }
                catch (Exception ex) { throw safeFailure(ex); }
            });
            if (preview.get() != null && preview.get().isShowing()) break;
            SystemClock.sleep(500);
        }
        if (preview.get() == null || !preview.get().isShowing() || pending.get() == null) throw new IllegalStateException("fixture_preview_missing");
        JSONObject payload = new JSONObject(pending.get());
        JSONArray courses = payload.getJSONArray("courses");
        if (courses.length() != 2) throw new IllegalStateException("fixture_course_count_wrong");
        JSONObject math = courses.getJSONObject(0);
        if (!math.getString("name").equals("高等数学") || !math.getString("teacher").equals("甲老师") || !math.getString("room").equals("教学楼101")
                || !math.getJSONArray("weeks").toString().equals("[1,3,5,7]")) throw new IllegalStateException("fixture_course_data_wrong");
        report.put("stage", "verify-native-preview");
        report.put("fixtureCourses", courses);
        report.put("nativePreviewVisible", preview.get().isShowing());
        report.put("previewNameAndRoomVisible", uiContains(preview.get(), "高等数学") && uiContains(preview.get(), "教学楼101"));
        report.put("previewWeeksVisible", uiContains(preview.get(), "1,3,5,7"));
        if (!report.getBoolean("previewNameAndRoomVisible")) throw new IllegalStateException("fixture_preview_text_wrong");
        if (!report.getBoolean("previewWeeksVisible")) throw new IllegalStateException("fixture_preview_weeks_wrong");
        checkedUi(() -> {
            View edit = findText(preview.get().getWindow().getDecorView(), "修改这条安排", true);
            if (edit == null || !edit.performClick()) throw new IllegalStateException("fixture_editor_missing");
        });
        report.put("stage", "verify-native-editor");
        AtomicReference<View> editorRoot = new AtomicReference<>();
        AtomicReference<JSONArray> editorWindows = new AtomicReference<>();
        long editorDeadline = SystemClock.elapsedRealtime() + 2000;
        waitForIdleSync();
        do {
            checkedUi(() -> {
                // Read the attached, focused dialog after its layout. ATD accessibility snapshots can be stale.
                JSONArray windows = new JSONArray();
                for (View root : WindowInspector.getGlobalWindowViews()) {
                    View teacher = findText(root, "甲老师", false);
                    Rect bounds = new Rect();
                    boolean field = teacher instanceof EditText;
                    boolean visible = field && teacher.isShown() && teacher.getGlobalVisibleRect(bounds) && !bounds.isEmpty();
                    JSONObject window = new JSONObject();
                    window.put("attached", root.isAttachedToWindow());
                    window.put("hasWindowFocus", root.hasWindowFocus());
                    window.put("teacherEditTextFound", field);
                    window.put("teacherVisible", visible);
                    windows.put(window);
                    if (root.isAttachedToWindow() && root.hasWindowFocus() && visible) editorRoot.set(root);
                }
                editorWindows.set(windows);
            });
            if (editorRoot.get() != null) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < editorDeadline);
        report.put("nativeEditorWindowObservations", editorWindows.get());
        report.put("nativeEditorTeacherVisible", editorRoot.get() != null);
        if (editorRoot.get() == null) throw new IllegalStateException("fixture_editor_teacher_wrong");
        report.put("stage", "cancel-fixture-without-saving");
        checkedUi(() -> {
            View cancel = findText(editorRoot.get(), "取消", true);
            if (cancel == null || !cancel.performClick()) throw new IllegalStateException("fixture_editor_cancel_failed");
        });
        waitForIdleSync();
        checkedUi(() -> {
            if (editorRoot.get().isAttachedToWindow()) throw new IllegalStateException("fixture_editor_still_attached");
            if (!preview.get().isShowing() || !preview.get().getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
                throw new IllegalStateException("fixture_preview_cancel_failed");
        });
        waitForIdleSync();
        checkedUi(() -> {
            if (preview.get().isShowing() || field(main, "pendingImport") != null || field(main, "importDialog") != null)
                throw new IllegalStateException("fixture_preview_still_pending");
        });
        String coursesAfter = getTargetContext().getSharedPreferences("kejian", 0).getString("courses", "<absent>");
        boolean unchanged = coursesBefore.equals(coursesAfter);
        report.put("fixturePreviewCancelLeavesDataUnchanged", unchanged);
        if (!unchanged) throw new IllegalStateException("fixture_changed_saved_courses");
        report.put("fixtureCancelledWithoutSaving", true);
    }

    private boolean uiContains(AlertDialog dialog, String expected) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        runOnMainSync(() -> found.set(findContaining(dialog.getWindow().getDecorView(), expected) != null));
        return found.get();
    }

    private String evaluate(WebView web, String script) throws Exception {
        CountDownLatch callback = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        runOnMainSync(() -> web.evaluateJavascript(script, text -> { value.set(text); callback.countDown(); }));
        if (!callback.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("webview_javascript_timeout");
        return value.get();
    }

    private interface CheckedAction { void run() throws Exception; }
    private static IllegalStateException safeFailure(Exception error) {
        String message = error.getMessage();
        if (error instanceof IllegalStateException && message != null && message.matches("[A-Za-z0-9_.-]{1,100}")) return (IllegalStateException)error;
        return new IllegalStateException(error.getClass().getName());
    }
    private void checkedUi(CheckedAction action) throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Exception error) { failure.set(error); } });
        if (failure.get() != null) throw failure.get();
    }


    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static View findText(View view, String text, boolean requireButton) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText()) && (!requireButton || view instanceof Button)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) { View found = findText(((ViewGroup)view).getChildAt(i), text, requireButton); if (found != null) return found; }
        return null;
    }
    private static View findContaining(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().contains(text)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) { View found = findContaining(((ViewGroup)view).getChildAt(i), text); if (found != null) return found; }
        return null;
    }
}
