package cn.kejian.timetable;

import org.json.*;

final class ImportPayload {
    static final int MAX_CHARS=64*1024;
    static String decodeJavascriptResult(String value) throws JSONException {
        if(value==null||value.length()>2*1024*1024)throw new IllegalArgumentException("页面返回的数据无法读取");
        Object decoded=new JSONTokener(value).nextValue();
        if(!(decoded instanceof String))throw new IllegalArgumentException("课表尚未加载完成，请等筛选完成后重试");
        String text=(String)decoded;JSONObject raw=new JSONObject(text);
        if(raw.has("error"))throw new IllegalArgumentException(raw.getString("error"));
        parse(text);return text;
    }
    static JSONObject parse(String text) throws JSONException {
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("没有读取到课表，请在个人课表页点击导课");
        if(text.length()>MAX_CHARS)throw new IllegalArgumentException("课表数据过大，请重新选择学期后导入");
        JSONObject payload=new JSONObject(text);
        if(!"kejian-import/1".equals(payload.optString("format")))throw new IllegalArgumentException("课表格式无法识别，请刷新页面后重试");
        if(payload.has("error"))throw new IllegalArgumentException(payload.optString("error"));
        if(Store.readCourses(payload.getJSONArray("courses")).isEmpty())throw new IllegalArgumentException("没有可导入的课程，请等待页面加载完成");
        JSONObject term=payload.optJSONObject("term");if(term!=null&&term.toString().length()>2000)throw new IllegalArgumentException("学期信息无效");
        JSONArray warnings=payload.optJSONArray("warnings");if(warnings!=null&&warnings.length()>1000)throw new IllegalArgumentException("核对提示过多，请重新选择学期");
        return payload;
    }
}
