package cn.kejian.timetable;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.*;
import android.text.InputType;
import android.text.InputFilter;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BrowserActivity extends Activity {
    WebView web;LinearLayout root;EditText address;TextView status,steps;Button importButton,desktopButton,landscapeButton;ProgressBar progress;
    SharedPreferences prefs;String mobileAgent,lastObservedUrl="",lastFailure="";boolean desktop,landscape,extracting=false,mainError=false,resumed=false;int pageEpoch=0;Handler handler=new Handler(Looper.getMainLooper());Button backButton,forwardButton;
    final Runnable urlMonitor=new Runnable(){@Override public void run(){if(!resumed||web==null)return;observeUrl(web.getUrl());updateNavigation();handler.postDelayed(this,750);}};
    final ArrayDeque<String> navigationLog=new ArrayDeque<>();
    final Set<String> repairedTargets=new HashSet<>();
    final long browserStarted=SystemClock.elapsedRealtime();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("kejian",MODE_PRIVATE);desktop=prefs.getBoolean("desktop",false);landscape=saved!=null&&saved.getBoolean("landscape",false);
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setFocusableInTouchMode(true);Ui.install(this,root);
        LinearLayout toolbar=Ui.row(this);toolbar.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,6));toolbar.addView(Ui.button(this,"关闭",false,this::finish),new LinearLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,48)));
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(13);address.setTextColor(Ui.INK);address.setHint("输入教务网址");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setSelectAllOnFocus(true);address.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});address.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));address.setBackground(Ui.shape(Color.WHITE,Ui.dp(this,12)));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);ap.setMargins(Ui.dp(this,6),0,Ui.dp(this,6),0);toolbar.addView(address,ap);toolbar.addView(Ui.button(this,"前往",false,()->open(address.getText().toString())),new LinearLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,48)));address.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){open(address.getText().toString());return true;}return false;});root.addView(toolbar);
        LinearLayout guide=Ui.row(this);guide.setPadding(Ui.dp(this,16),0,Ui.dp(this,10),0);guide.addView(Ui.text(this,"找到个人 / 学期课表",13,Ui.GREEN,true),new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));guide.addView(Ui.button(this,"步骤",false,()->{steps.setVisibility(steps.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}));root.addView(guide);
        steps=Ui.text(this,"登录 → 我的选课 → 个人课表 → 选择学期并筛选 → 导课\n等课程加载完成后再导入，可在预览中修改和核对。",12,Ui.MUTED,false);steps.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,8));steps.setVisibility(View.GONE);root.addView(steps);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,3)));
        FrameLayout page=new FrameLayout(this);root.addView(page,new LinearLayout.LayoutParams(-1,0,1));web=new WebView(this);page.addView(web,new FrameLayout.LayoutParams(-1,-1));
        importButton=Ui.button(this,"导课",true,this::extract);importButton.setBackground(Ui.shape(Ui.GREEN,Ui.dp(this,40)));importButton.setEnabled(false);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76),Gravity.END|Gravity.BOTTOM);ip.setMargins(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));page.addView(importButton,ip);
        status=Ui.text(this,"请在学校页面登录并打开个人课表。",12,Ui.MUTED,false);status.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,2));root.addView(status);
        LinearLayout navigation=Ui.row(this);navigation.setPadding(Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10),0);backButton=Ui.button(this,"后退",false,()->{if(web!=null&&web.canGoBack())web.goBack();});forwardButton=Ui.button(this,"前进",false,()->{if(web!=null&&web.canGoForward())web.goForward();});Button refresh=Ui.button(this,"刷新",false,this::refresh);Button login=Ui.button(this,"重新登录",false,this::openLogin);for(Button button:new Button[]{backButton,forwardButton,refresh,login}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,44),1);p.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);button.setTextSize(12);button.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),0);navigation.addView(button,p);}root.addView(navigation);
        LinearLayout tools=Ui.row(this);tools.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,10));desktopButton=Ui.button(this,"电脑模式",false,this::toggleDesktop);landscapeButton=Ui.button(this,"横屏模式",false,this::toggleLandscape);Button help=Ui.button(this,"打不开？",false,this::help);for(Button button:new Button[]{desktopButton,landscapeButton,help}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);tools.addView(button,p);}root.addView(tools);updateModeLabels();
        WebSettings settings=web.getSettings();mobileAgent=settings.getUserAgentString();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setSupportZoom(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setMediaPlaybackRequiresUserGesture(true);
        // Keep new-window links and form submissions here. Never reissue their requests as GET.
        settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(true);applyAgent();CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            // Only the two reported GET callback defects are corrected; ordinary navigation stays native.
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                String original=request.getUrl().toString(),corrected=BrowserPolicy.repair(original,request.getMethod());
                if(corrected!=null){
                    if(repairedTargets.size()>=6||!repairedTargets.add(corrected)){mainError=true;lastFailure="学校登录重复回跳";progress.setVisibility(View.GONE);importButton.setEnabled(false);record("停止重复回跳",original);status.setText("学校登录重复回跳，请点重新登录开启新流程。");return true;}
                    record(original.startsWith("http:")?"升级登录中转为HTTPS":"修正登录返回地址",corrected);
                    view.loadUrl(corrected,new HashMap<>(request.getRequestHeaders()));return true;
                }
                return blockNavigation(original);
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){pageEpoch++;lastObservedUrl=url==null?"":url;mainError=false;lastFailure="";extracting=false;importButton.setText("导课");importButton.setEnabled(false);setAddress(url);progress.setProgress(0);progress.setVisibility(View.VISIBLE);record("打开",url);status.setText(BrowserPolicy.identity(url)?"正在打开同济统一身份认证…":"正在打开网页…");watchNavigation(pageEpoch);}
            @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){observeUrl(url);updateNavigation();}
            @Override public void onPageCommitVisible(WebView view,String url){setAddress(url);updatePageControls(url);}
            @Override public void onPageFinished(WebView view,String url){if(!Objects.equals(url,view.getUrl()))return;setAddress(view.getUrl());progress.setVisibility(View.GONE);updateNavigation();record("完成",url);if(mainError){importButton.setEnabled(false);return;}updatePageControls(view.getUrl());CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){record("网络错误 "+error.getErrorCode(),r.getUrl().toString());if(r.isForMainFrame()){mainError=true;lastFailure="网络错误 "+error.getErrorCode();progress.setVisibility(View.GONE);importButton.setEnabled(false);status.setText("网页加载失败（"+error.getErrorCode()+"）。点「打不开？」查看信息或重试。");}}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(response.getStatusCode()>=400){record("HTTP "+response.getStatusCode(),r.getUrl().toString());if(r.isForMainFrame()){mainError=true;lastFailure="HTTP "+response.getStatusCode();progress.setVisibility(View.GONE);importButton.setEnabled(false);status.setText("网页返回 "+response.getStatusCode()+"。点「打不开？」查看信息或重试。");}}}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();record("证书错误 "+e.getPrimaryError(),e.getUrl());if(Objects.equals(v.getUrl(),e.getUrl())||Objects.equals(lastObservedUrl,e.getUrl())){mainError=true;lastFailure="证书错误 "+e.getPrimaryError();progress.setVisibility(View.GONE);importButton.setEnabled(false);status.setText("网站证书验证失败，请检查网络后重试。");}}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){((ViewGroup)v.getParent()).removeView(v);v.destroy();web=null;importButton.setEnabled(false);new AlertDialog.Builder(BrowserActivity.this).setTitle("页面已关闭").setMessage("浏览器页面被系统回收，请返回后重新打开。").setCancelable(false).setPositiveButton("返回",(dialog,which)->finish()).show();return true;}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int value){progress.setProgress(value);}
            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(BrowserActivity.this).setTitle("学校网页提示").setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){new AlertDialog.Builder(BrowserActivity.this).setTitle("学校网页提示").setMessage(message).setPositiveButton("确定",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}

        });
        web.setDownloadListener((url,agent,contentDisposition,mimeType,length)->new AlertDialog.Builder(this).setTitle("直接读取课表").setMessage("请回到个人课表页点击「导课」，无需下载课表文件。").setPositiveButton("知道了",null).show());
        root.requestFocus();if(saved==null||web.restoreState(saved)==null)openLogin();else if(BrowserPolicy.callback(web.getUrl())||BrowserPolicy.loginBridge(web.getUrl())||BrowserPolicy.repair(web.getUrl(),"GET")!=null)openLogin();if(landscape)setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    void setAddress(String url){if(url!=null&&!address.hasFocus())address.setText(url);}
    void open(String input){if(web==null)return;try{String url=BrowserPolicy.normalize(input),corrected=BrowserPolicy.repair(url,"GET");if(corrected!=null)url=corrected;repairedTargets.clear();address.clearFocus();root.requestFocus();address.setText(url);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);web.loadUrl(url);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    void openLogin(){if(web!=null)web.stopLoading();open(BrowserPolicy.HOME);}
    void refresh(){if(web==null)return;if(BrowserPolicy.callback(web.getUrl())||BrowserPolicy.loginBridge(web.getUrl())||BrowserPolicy.repair(web.getUrl(),"GET")!=null)openLogin();else web.reload();}
    void updateNavigation(){if(web!=null){backButton.setEnabled(web.canGoBack());forwardButton.setEnabled(web.canGoForward());}}
    void updatePageControls(String url){if(mainError||extracting)return;importButton.setEnabled(BrowserPolicy.timetable(url));if(BrowserPolicy.timetable(url))status.setText("等学期筛选完成后，点击导课。");else if(BrowserPolicy.identity(url))status.setText("同济统一身份认证，请在网页中登录。");else if(BrowserPolicy.callback(url))status.setText("学校登录返回接口已加载，等待进入教务系统…");else if(BrowserPolicy.loginBridge(url))status.setText("正在完成教务登录，请稍候…");else status.setText("请在学校页面登录，再打开个人课表。");}
    void observeUrl(String url){if(url==null||url.equals(lastObservedUrl))return;lastObservedUrl=url;pageEpoch++;extracting=false;importButton.setText("导课");record("跳转",url);setAddress(url);updatePageControls(url);watchNavigation(pageEpoch);}

    void watchNavigation(int epoch){handler.postDelayed(()->{if(web==null||isFinishing()||isDestroyed()||pageEpoch!=epoch||mainError)return;boolean handoff=BrowserPolicy.callback(web.getUrl())||BrowserPolicy.loginBridge(web.getUrl());if(progress.getVisibility()!=View.VISIBLE&&!handoff)return;lastFailure=handoff?"学校登录返回尚未完成跳转":"网页加载等待超过20秒";status.setText(handoff?"学校登录返回还未完成，可点重新登录开启新流程。":"网页响应较慢，可继续等待，或点「打不开？」查看信息。");},20000);}
    boolean blockNavigation(String url){if(BrowserPolicy.allowed(url))return false;record("未打开",url);status.setText("此链接无法在网页中打开，请继续使用网页登录。");return true;}
    void applyAgent(){if(web!=null)web.getSettings().setUserAgentString(desktop?BrowserPolicy.desktopAgent(mobileAgent):BrowserPolicy.browserAgent(mobileAgent));}
    String pageLocation(String url){try{java.net.URI uri=java.net.URI.create(url);return uri.getScheme()+"://"+uri.getHost()+(uri.getPort()==-1?"":":"+uri.getPort())+(uri.getPath()==null?"":uri.getPath());}catch(Exception e){return "未知页面";}}
    void record(String action,String url){if(navigationLog.size()>=24)navigationLog.removeFirst();navigationLog.addLast(((SystemClock.elapsedRealtime()-browserStarted)/1000)+"秒 "+action+" "+pageLocation(url));}
    String callbackFlags(String url){try{String query=java.net.URI.create(url).getRawQuery();Set<String> names=new HashSet<>();if(query!=null)for(String part:query.split("&")){int split=part.indexOf('=');names.add(java.net.URLDecoder.decode(split<0?part:part.substring(0,split),"UTF-8"));}return "code="+(names.contains("code")?"有":"无")+"，state="+(names.contains("state")?"有":"无")+"，uid="+(names.contains("uid")?"有":"无")+"，token="+(names.contains("token")?"有":"无");}catch(Exception e){return "无法识别";}}
    void updateModeLabels(){desktopButton.setText(desktop?"手机模式":"电脑模式");landscapeButton.setText(landscape?"回到竖屏":"横屏模式");}
    void toggleDesktop(){if(web==null)return;desktop=!desktop;prefs.edit().putBoolean("desktop",desktop).apply();applyAgent();updateModeLabels();refresh();}
    void toggleLandscape(){landscape=!landscape;updateModeLabels();setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}
    void help(){new AlertDialog.Builder(this).setTitle("页面打不开或导入失败？").setItems(new String[]{"刷新当前页面","重新打开学校登录页","清除登录缓存后重新登录","查看导入步骤","查看加载信息"},(d,n)->{if(web==null)return;if(n==0)refresh();else if(n==1)openLogin();else if(n==2)new AlertDialog.Builder(this).setTitle("清除教务登录缓存？").setMessage("已保存的课表保留，网页需要重新登录。").setPositiveButton("清除",(dd,w)->{CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();if(web!=null){web.clearCache(true);web.clearHistory();openLogin();}});}).setNegativeButton("取消",null).show();else if(n==3){steps.setVisibility(View.VISIBLE);status.setText("请确认学期筛选已完成。可以切换电脑模式后再尝试。");}else diagnostics();}).setNegativeButton("关闭",null).show();}
    void diagnostics(){android.content.pm.PackageInfo engine=WebView.getCurrentWebViewPackage();TextView info=Ui.text(this,"应用版本：0.3.3\n当前页面："+pageLocation(web==null?null:web.getUrl())+"\n回调参数（不显示值）："+callbackFlags(web==null?null:web.getUrl())+"\n加载进度："+progress.getProgress()+"%\n状态："+status.getText()+"\n故障信息："+(lastFailure.isEmpty()?"暂未收到网络错误":lastFailure)+"\n浏览组件："+(engine==null?"未知":engine.packageName+" "+engine.versionName)+"\n安卓版本："+Build.VERSION.RELEASE+"\n\n最近加载记录：\n"+String.join("\n",navigationLog),13,Ui.INK,false);info.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12));info.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(info);new AlertDialog.Builder(this).setTitle("加载信息").setView(scroll).setPositiveButton("关闭",null).show();}
    void extract(){
        if(web==null||extracting)return;if(!BrowserPolicy.timetable(web.getUrl())){status.setText("请先打开个人课表页。");return;}final int epoch=pageEpoch;extracting=true;importButton.setEnabled(false);importButton.setText("读取中");status.setText("正在读取完整课程和上课周次…");
        Runnable timeout=()->{if(extracting&&pageEpoch==epoch&&!isFinishing()&&!isDestroyed()){extracting=false;importButton.setText("导课");importButton.setEnabled(web!=null&&BrowserPolicy.timetable(web.getUrl()));status.setText("读取超时，请等页面加载完成后重试。");}};handler.postDelayed(timeout,10000);
        try{String script=MainActivity.read(getAssets().open("tongji-extract.js"));web.evaluateJavascript(script,value->{handler.removeCallbacks(timeout);if(isFinishing()||isDestroyed()||pageEpoch!=epoch||!extracting)return;extracting=false;importButton.setText("导课");importButton.setEnabled(web!=null&&BrowserPolicy.timetable(web.getUrl()));try{
            if(web==null||!BrowserPolicy.timetable(web.getUrl()))throw new IllegalArgumentException("页面已跳转，请回到课表再导课");String text=ImportPayload.decodeJavascriptResult(value);
            File target=new File(getCacheDir(),"import-preview.json");android.util.AtomicFile file=new android.util.AtomicFile(target);FileOutputStream stream=null;try{stream=file.startWrite();stream.write(text.getBytes(StandardCharsets.UTF_8));file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
            setResult(RESULT_OK);finish();
        }catch(Exception e){status.setText("尚未导入，课程数据保留不变。");new AlertDialog.Builder(this).setTitle("请核对当前页面").setMessage(e.getMessage()).setPositiveButton("返回页面",null).show();}});
        }catch(Exception e){handler.removeCallbacks(timeout);extracting=false;importButton.setText("导课");importButton.setEnabled(true);status.setText("读取失败，请刷新页面后重试。");}
    }
    void back(){if(web!=null&&web.canGoBack())web.goBack();else finish();}
    @Override public void onBackPressed(){back();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);root.requestApplyInsets();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);if(web!=null)web.saveState(saved);saved.putBoolean("landscape",landscape);}
    @Override protected void onResume(){super.onResume();resumed=true;if(web!=null)web.onResume();handler.removeCallbacks(urlMonitor);handler.post(urlMonitor);}
    @Override protected void onPause(){super.onPause();resumed=false;handler.removeCallbacks(urlMonitor);if(web!=null)web.onPause();CookieManager.getInstance().flush();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null){((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
