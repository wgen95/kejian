package cn.kejian.timetable;

public class BrowserPolicyTest {
    static int checks=0;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static void reject(String text){try{BrowserPolicy.normalize(text);throw new AssertionError("accepted "+text);}catch(IllegalArgumentException expected){checks++;}}
    static void repairTo(String source,String target){
        check(target.equals(BrowserPolicy.repair(source,"GET")),"repair exact target and raw parameters");
        check(BrowserPolicy.allowed(target),"repair target uses allowed HTTPS");
        check(BrowserPolicy.repair(target,"GET")==null,"repaired target does not loop");
    }
    public static void main(String[] args){
        check(BrowserPolicy.normalize(" 1.tongji.edu.cn ").equals("https://1.tongji.edu.cn"),"normalize hostname");
        check(BrowserPolicy.HOME.equals("https://1.tongji.edu.cn/"),"start at school homepage");
        String freshLogin="https://iam.tongji.edu.cn:443/idp/authcenter/ActionAuthChain?entityId=SYS20230001&authnLcKey=synthetic-session-key";
        check(BrowserPolicy.allowed(freshLogin),"complete generated IAM URL allowed");check(BrowserPolicy.identity(freshLogin),"complete generated IAM URL recognized");check(BrowserPolicy.normalize(freshLogin).equals(freshLogin),"preserve IAM URL and parameters");
        check(BrowserPolicy.allowed("https://1.TONGJI.edu.cn:443/"),"case and default TLS port");
        check(BrowserPolicy.timetable("https://1.tongji.edu.cn/studentTimeTable?calendarId=123"),"personal timetable");
        check(BrowserPolicy.timetable("https://1.tongji.edu.cn/GraduateStudentTimeTable"),"user supplied page");
        check(BrowserPolicy.timetable("https://1.tongji.edu.cn/StudentSchedule/"),"alternate personal schedule");
        check(!BrowserPolicy.timetable("https://1.tongji.edu.cn/studentTimeTableAdmin"),"administrative route excluded");
        check(!BrowserPolicy.timetable("https://iam.tongji.edu.cn/studentTimeTable"),"cannot extract identity page");
        check(!BrowserPolicy.timetable("https://1.tongji.edu.cn/"),"cannot extract portal");
        check(!BrowserPolicy.timetable("https://1.tongji.edu.cn/studentTimeTable%2F"),"encoded path is not exact course route");
        check(BrowserPolicy.allowed("https://example.org/help"),"normal external HTTPS link");check(BrowserPolicy.allowed("https://service.example.org:8443/signin"),"normal HTTPS service port");check(BrowserPolicy.normalize("example.org/help").equals("https://example.org/help"),"external address input");
        for(String prefix:new String[]{"https://example.org", "https://1.tongji.edu.cn.example.org", "https://not-tongji.edu.cn", "https://1.tongji.edu.cn:8443", "https://fake.1.tongji.edu.cn"}){
            check(BrowserPolicy.allowed(prefix+"/studentTimeTable"),"HTTPS navigation available");check(!BrowserPolicy.timetable(prefix+"/studentTimeTable"),"external or nonstandard origin cannot import");check(!BrowserPolicy.loginBridge(prefix+"/ssologin"),"external or nonstandard origin cannot resemble callback");
        }
        for(String s:new String[]{null,"", "http://1.tongji.edu.cn/studentTimeTable", "javascript:alert(1)", "file:///data/data/", "intent://login", "https://1.tongji.edu.cn@evil.example/studentTimeTable", "https://user:password@1.tongji.edu.cn/studentTimeTable", "https://user@iam.tongji.edu.cn/idp/", "https://user@1.tongji.edu.cn/ssologin", "https:///studentTimeTable", "https://1.tongji.edu.cn:0/", "https://1.tongji.edu.cn:65536/"}){
            reject(s);check(!BrowserPolicy.allowed(s),"invalid browser navigation rejected");check(!BrowserPolicy.timetable(s),"invalid origin cannot import");check(!BrowserPolicy.identity(s),"invalid origin cannot resemble IAM");check(!BrowserPolicy.loginBridge(s),"invalid origin cannot resemble callback");
        }
        String mobile="Mozilla/5.0 (Linux; Android 16; RMX5060 Build/A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.2.1234.56 Mobile Safari/537.36";
        String browser=BrowserPolicy.browserAgent(mobile);check(browser.contains("Chrome/147.2.1234.56"),"keep provider Chromium version in browser mode");check(browser.contains("Android 16; RMX5060 Build/A")&&browser.contains(" Mobile"),"keep actual mobile platform");check(!browser.contains("; wv")&&!browser.contains("Version/4.0 "),"remove WebView wrapper markers");
        String desktop=BrowserPolicy.desktopAgent(mobile);check(desktop.contains("Chrome/147.2.1234.56"),"keep provider Chromium version in desktop mode");check(desktop.contains("X11; Linux x86_64")&&!desktop.contains(" Mobile")&&!desktop.contains("; wv")&&!desktop.contains("Version/4.0 "),"desktop UA based on browser UA");
        check(BrowserPolicy.browserAgent(browser).equals(browser),"browser UA processing repeatable");check(BrowserPolicy.desktopAgent(desktop).equals(desktop),"desktop UA processing repeatable");String newer=mobile.replace("147.2.1234.56","151.9.7654.32");check(BrowserPolicy.browserAgent(newer).contains("Chrome/151.9.7654.32"),"browser follows provider version update");check(BrowserPolicy.desktopAgent(newer).contains("Chrome/151.9.7654.32"),"desktop follows provider version update");
        check(BrowserPolicy.identity("https://iam.tongji.edu.cn/idp/authcenter/ActionAuthChain?entityId=SYS20230001"),"recognize login page");
        check(!BrowserPolicy.identity("https://iam.tongji.edu.cn.example.org/idp/"),"reject forged IAM host");check(!BrowserPolicy.identity("https://fake.iam.tongji.edu.cn/idp/"),"IAM host exact");check(!BrowserPolicy.identity("https://iam.tongji.edu.cn:8443/idp/"),"IAM port exact");
        for(String callback:new String[]{"https://1.tongji.edu.cn/ssologin", "https://1.tongji.edu.cn/sslogin", "https://1.tongji.edu.cn/ssologin?uid=123", "https://1.tongji.edu.cn/ssologin?uid=123&token=synthetic&ts=123", "https://1.tongji.edu.cn:443/ssologin?uid=123&token=a%26b%3Dc#keep"}){
            check(BrowserPolicy.normalize(callback).equals(callback),"leave school callback untouched");check(BrowserPolicy.loginBridge(callback),"callback recognized only for UI");
        }
        check(!BrowserPolicy.loginBridge("https://iam.tongji.edu.cn/ssologin"),"identity is not course callback");check(!BrowserPolicy.loginBridge("https://1.tongji.edu.cn/studentTimeTable"),"course table is not callback");
        String loginPath="/api/ssoservice/system/loginIn";
        String badSuffix=";https://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn";
        String goodLogin="https://1.tongji.edu.cn"+loginPath;
        String[] rawTails={"", "?code=synthetic%26value&state=a+b%2Bc", "?uid=a%26b&token=x+y&token=z&ts=1#f%2F+", "?x=%252F&x=%2f&x=+%20#raw%2f", "?", "#", "?#", "?redirect_uri=https%3A%2F%2Fexample.org%2Freturn%3Bx&state=synthetic#keep"};
        for(String httpOrigin:new String[]{"http://1.tongji.edu.cn", "http://1.tongji.edu.cn:80", "HTTP://1.TONGJI.edu.cn:80"})for(String path:new String[]{"/ssologin","/sslogin"})for(String tail:rawTails){
            repairTo(httpOrigin+path+tail,"https://1.tongji.edu.cn"+path+tail);
            check(!BrowserPolicy.allowed(httpOrigin+path+tail),"HTTP callback remains blocked as cleartext");
            check(!BrowserPolicy.timetable(httpOrigin+path+tail),"repair cannot enable HTTP extraction");
        }
        String[] suffixes={badSuffix, "%3Bhttps%3A%2F%2F1-443.webvpn.tongji.edu.cn%2F%3Bhttps%3A%2F%2Fonetest.tongji.edu.cn", ";https%3a%2f%2f1-443.webvpn.tongji.edu.cn%2f;https%3a%2f%2fonetest.tongji.edu.cn"};
        for(String httpsOrigin:new String[]{"https://1.tongji.edu.cn", "https://1.tongji.edu.cn:443", "HTTPS://1.TONGJI.edu.cn:443"})for(String suffix:suffixes)for(String tail:rawTails){
            repairTo(httpsOrigin+loginPath+suffix+tail,goodLogin+tail);
            check(!BrowserPolicy.callback(httpsOrigin+loginPath+suffix+tail),"malformed API callback is not normal UI callback");
        }
        for(String tail:rawTails){
            check(BrowserPolicy.callback(goodLogin+tail),"exact normal API callback recognized for UI");
            check(BrowserPolicy.callback("https://1.TONGJI.edu.cn:443"+loginPath+tail),"normal callback host case and TLS port");
            check(BrowserPolicy.repair(goodLogin+tail,"GET")==null,"normal API callback remains untouched");
        }
        for(String method:new String[]{"POST","HEAD","PUT","OPTIONS","get","",null})for(String url:new String[]{"http://1.tongji.edu.cn/ssologin?token=synthetic",goodLogin+badSuffix+"?code=synthetic&state=synthetic"}){
            check(BrowserPolicy.repair(url,method)==null,"only exact GET navigation repaired");
        }
        String[] excludedRepairUrls={
            null,"","not a URI","https:///ssologin","javascript:alert(1)",
            "http://example.org/ssologin","http://1.tongji.edu.cn.example.org/ssologin","http://fake.1.tongji.edu.cn/ssologin","http://1.tongji.edu.cn./ssologin",
            "http://user@1.tongji.edu.cn/ssologin","http://1.tongji.edu.cn:443/ssologin","http://1.tongji.edu.cn:81/ssologin","http://1.tongji.edu.cn:0/ssologin",
            "http://1.tongji.edu.cn/ssologin/","http://1.tongji.edu.cn/SSOLOGIN","http://1.tongji.edu.cn/%73sologin","http://1.tongji.edu.cn//ssologin","http://1.tongji.edu.cn/x/../ssologin","http://1.tongji.edu.cn/ssologin;other",
            "http://1.tongji.edu.cn/studentTimeTable","http://1.tongji.edu.cn"+loginPath+badSuffix,
            "https://example.org"+loginPath+badSuffix,"https://1.tongji.edu.cn.example.org"+loginPath+badSuffix,"https://fake.1.tongji.edu.cn"+loginPath+badSuffix,"https://1.tongji.edu.cn."+loginPath+badSuffix,
            "https://user@1.tongji.edu.cn"+loginPath+badSuffix,"https://1.tongji.edu.cn:80"+loginPath+badSuffix,"https://1.tongji.edu.cn:8443"+loginPath+badSuffix,"https://1.tongji.edu.cn:65536"+loginPath+badSuffix,
            "https://1.tongji.edu.cn/%61pi/ssoservice/system/loginIn"+badSuffix,"https://1.tongji.edu.cn/api/ssoservice/system/%6CoginIn"+badSuffix,"https://1.tongji.edu.cn%2F"+loginPath+badSuffix,
            "https://1.tongji.edu.cn//api/ssoservice/system/loginIn"+badSuffix,"https://1.tongji.edu.cn/x/.."+loginPath+badSuffix,"https://1.tongji.edu.cn/api/ssoservice/system/loginIn/"+badSuffix,
            goodLogin+"%253Bhttps%253A%252F%252F1-443.webvpn.tongji.edu.cn%252F%253Bhttps%253A%252F%252Fonetest.tongji.edu.cn",
            goodLogin+badSuffix+"/",goodLogin+badSuffix+";other",goodLogin+";https://1-443.webvpn.tongji.edu.cn/",goodLogin+";https://onetest.tongji.edu.cn",
            goodLogin+";https://onetest.tongji.edu.cn/;https://1-443.webvpn.tongji.edu.cn",goodLogin+";https://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn.evil.example",
            goodLogin+";HTTPS://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn",goodLogin+"%5C;https://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn",
            goodLogin+";https://1-443.webvpn.tongji.edu.cn/../;https://onetest.tongji.edu.cn",goodLogin+";https://1-443.webvpn.tongji.edu.cn/;%2Fhttps://onetest.tongji.edu.cn",
            goodLogin+"?redirect_uri="+badSuffix,goodLogin+"#"+badSuffix,"https://1.tongji.edu.cn/studentTimeTable"+badSuffix
        };
        for(String url:excludedRepairUrls)check(BrowserPolicy.repair(url,"GET")==null,"unreported navigation not rewritten");
        for(String url:new String[]{null,"", "http://1.tongji.edu.cn"+loginPath,"https://example.org"+loginPath,"https://1.tongji.edu.cn:8443"+loginPath,"https://user@1.tongji.edu.cn"+loginPath,"https://1.tongji.edu.cn"+loginPath+"/","https://1.tongji.edu.cn/api/ssoservice/system/%6CoginIn",goodLogin+badSuffix,"https://1.tongji.edu.cn/ssologin"}){
            check(!BrowserPolicy.callback(url),"API callback recognition stays exact and HTTPS only");
        }
        System.out.println("Browser policy tests passed: "+checks);
    }
}
