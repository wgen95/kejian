package cn.kejian.timetable;
import org.json.*;
import java.util.*;

public class ImportPayloadTest {
    static int checks=0;
    interface Checked {void run() throws Exception;}
    static void reject(Checked action) throws Exception {try{action.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException|JSONException expected){checks++;}}
    static JSONObject good() throws JSONException {Course c=new Course();c.name="高等数学";c.weeks.addAll(Course.parseWeeks("1-16单"));JSONObject o=new JSONObject();o.put("format","kejian-import/1");o.put("courses",Store.encodeCourses(Arrays.asList(c)));o.put("term",new JSONObject().put("name","秋季"));return o;}
    public static void main(String[] args)throws Exception {
        if(ImportPayload.parse(good().toString()).getJSONArray("courses").length()!=1)throw new AssertionError();checks++;
        reject(()->ImportPayload.parse(null));reject(()->ImportPayload.parse(""));reject(()->ImportPayload.parse("https://1.tongji.edu.cn/studentTimeTable"));
        JSONObject wrong=good().put("format","other");reject(()->ImportPayload.parse(wrong.toString()));
        JSONObject empty=good().put("courses",new JSONArray());reject(()->ImportPayload.parse(empty.toString()));
        JSONObject bad=good();bad.getJSONArray("courses").getJSONObject(0).put("day",0);reject(()->ImportPayload.parse(bad.toString()));
        JSONObject decimal=good();decimal.getJSONArray("courses").getJSONObject(0).put("start",1.5);reject(()->ImportPayload.parse(decimal.toString()));
        JSONObject error=good().put("error","未登录");reject(()->ImportPayload.parse(error.toString()));
        char[] large=new char[ImportPayload.MAX_CHARS+1];Arrays.fill(large,'x');reject(()->ImportPayload.parse(new String(large)));
        JSONObject term=good().put("term",new JSONObject().put("name",new String(new char[2001])));reject(()->ImportPayload.parse(term.toString()));
        String text=good().toString();String callback=JSONObject.quote(text);if(!ImportPayload.decodeJavascriptResult(callback).equals(text))throw new AssertionError("WebView callback decoded incorrectly");checks++;
        reject(()->ImportPayload.decodeJavascriptResult(null));reject(()->ImportPayload.decodeJavascriptResult("null"));reject(()->ImportPayload.decodeJavascriptResult(text));reject(()->ImportPayload.decodeJavascriptResult("[]"));reject(()->ImportPayload.decodeJavascriptResult(JSONObject.quote("null")));
        reject(()->ImportPayload.decodeJavascriptResult(JSONObject.quote(new JSONObject().put("error","请打开个人课表").toString())));
        System.out.println("Import payload tests passed: "+checks);
    }
}
