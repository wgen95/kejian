package cn.kejian.timetable;

import java.net.URI;

final class BrowserPolicy {
    static final String HOME="https://1.tongji.edu.cn/";
    private static final String LOGIN_PATH="/api/ssoservice/system/loginIn";
    private static final String REPORTED_LOGIN_SUFFIX=";https://1-443.webvpn.tongji.edu.cn/;https://onetest.tongji.edu.cn";
    static String normalize(String input) {
        String s=input==null?"":input.trim();if(s.isEmpty())throw new IllegalArgumentException("请输入教务网址");
        if(!s.matches("(?i)^[a-z][a-z0-9+.-]*:.*"))s="https://"+s;
        if(!allowed(s))throw new IllegalArgumentException("请输入有效的 HTTPS 网址");
        return URI.create(s).toASCIIString();
    }
    static boolean allowed(String url) {
        try{URI u=URI.create(url);int port=u.getPort();return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null&&(port==-1||(port>0&&port<=65535));}catch(Exception e){return false;}
    }
    // Navigation follows ordinary HTTPS links. Only exact official origins can
    // enable timetable extraction or display school authentication UI states.
    private static boolean officialHost(String url,String host) {
        if(!allowed(url))return false;URI u=URI.create(url);return host.equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==443);
    }
    static boolean timetable(String url) {
        if(!officialHost(url,"1.tongji.edu.cn"))return false;String path=URI.create(url).getRawPath();return path!=null&&path.matches("/(studentTimeTable|StudentSchedule|GraduateStudentTimeTable)/?");
    }
    static boolean identity(String url){return officialHost(url,"iam.tongji.edu.cn");}
    static boolean loginBridge(String url){if(!officialHost(url,"1.tongji.edu.cn"))return false;String path=URI.create(url).getRawPath();return path!=null&&path.matches("/(?i:ssologin|sslogin)/?");}
    static boolean callback(String url){return officialHost(url,"1.tongji.edu.cn")&&LOGIN_PATH.equals(URI.create(url).getRawPath());}
    // Repair only the two observed GET navigation defects. Never send credentials over HTTP,
    // reinterpret a POST, or decode/rebuild the school's query values.
    static String repair(String url,String method){
        if(!"GET".equals(method))return null;
        try{
            URI u=URI.create(url);String scheme=u.getScheme(),path=u.getRawPath();int port=u.getPort();
            if(!"1.tongji.edu.cn".equalsIgnoreCase(u.getHost())||u.getRawUserInfo()!=null)return null;
            String destinationPath;
            if("http".equalsIgnoreCase(scheme)&&(port==-1||port==80)&&("/ssologin".equals(path)||"/sslogin".equals(path))){
                destinationPath=path;
            }else if("https".equalsIgnoreCase(scheme)&&(port==-1||port==443)&&path!=null&&path.startsWith(LOGIN_PATH)&&
                    (LOGIN_PATH+REPORTED_LOGIN_SUFFIX).equals(u.getPath())){
                // The normal prefix must be literal. Only the exact known suffix may be encoded.
                destinationPath=LOGIN_PATH;
            }else return null;
            return "https://1.tongji.edu.cn"+destinationPath+(u.getRawQuery()==null?"":"?"+u.getRawQuery())+(u.getRawFragment()==null?"":"#"+u.getRawFragment());
        }catch(Exception e){return null;}
    }
    static String browserAgent(String webViewAgent){return webViewAgent.replace("; wv", "").replace("Version/4.0 ","");}
    static String desktopAgent(String webViewAgent){return browserAgent(webViewAgent).replaceAll("\\(Linux; Android[^)]*\\)","(X11; Linux x86_64)").replace(" Mobile", "");}
}
