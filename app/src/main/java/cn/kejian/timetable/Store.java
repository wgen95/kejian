package cn.kejian.timetable;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

final class Store {
    final SharedPreferences prefs;
    final List<Course> courses=new ArrayList<>();
    static final String[] DEFAULT_TIMES={"08:00-08:45","08:55-09:40","10:00-10:45","10:55-11:40","13:30-14:15","14:25-15:10","15:30-16:15","16:25-17:10","18:30-19:15","19:25-20:10","20:20-21:05","21:15-22:00"};
    boolean loadFailed=false;
    Store(Context context) { this(context.getSharedPreferences("kejian",Context.MODE_PRIVATE)); }
    Store(SharedPreferences preferences) {
        prefs=preferences;
        try{courses.addAll(readCourses(new JSONArray(prefs.getString("courses","[]"))));}catch(Exception e){loadFailed=true;}
    }
    void save() {
        int required=totalWeeks();for(Course c:courses)required=Math.max(required,c.weeks.last());
        if(!prefs.edit().putString("courses",encodeCourses(courses).toString()).putInt("totalWeeks",required).commit()) throw new IllegalStateException("保存失败，请检查手机存储空间");
        loadFailed=false;
    }
    LocalDate monday(){return LocalDate.parse(prefs.getString("monday",LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()));}
    boolean termSet(){return prefs.contains("monday");}
    int totalWeeks(){return prefs.getInt("totalWeeks",20);}
    String termName(){return prefs.getString("termName","我的学期");}
    String[] times(){return prefs.getString("times",String.join("\n",DEFAULT_TIMES)).split("\n");}
    static JSONObject encode(Course c) {
        JSONObject o=new JSONObject();try{o.put("id",c.id);o.put("name",c.name);o.put("room",c.room);o.put("teacher",c.teacher);o.put("note",c.note);o.put("day",c.day);o.put("start",c.start);o.put("end",c.end);o.put("color",c.color);o.put("weeks",new JSONArray(c.weeks));}catch(JSONException e){throw new IllegalStateException(e);}return o;
    }
    static JSONArray encodeCourses(List<Course> list){JSONArray a=new JSONArray();for(Course c:list)a.put(encode(c));return a;}
    static List<Course> readCourses(JSONArray list) throws JSONException {
        if(list.length()>1000)throw new IllegalArgumentException("课程数量过多");
        List<Course> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<list.length();i++) {
            JSONObject o=list.getJSONObject(i); Course c=new Course();c.id=o.optString("id",c.id);c.name=o.getString("name");c.room=o.optString("room");c.teacher=o.optString("teacher");c.note=o.optString("note");c.day=strictInt(o.get("day"));c.start=strictInt(o.get("start"));c.end=strictInt(o.get("end"));c.color=o.has("color")?strictInt(o.get("color")):i%6;
            JSONArray w=o.getJSONArray("weeks");for(int j=0;j<w.length();j++)c.weeks.add(strictInt(w.get(j))); c.validate(); if(!ids.add(c.id)){c.id=UUID.randomUUID().toString();ids.add(c.id);} result.add(c);
        }return result;
    }
    static int strictInt(Object value){String s=String.valueOf(value);if(!s.matches("[0-9]{1,3}"))throw new IllegalArgumentException("星期、节次和周次需为整数");return Integer.parseInt(s);}
    JSONObject backup() throws JSONException {JSONObject o=new JSONObject();o.put("format","kejian/1");o.put("courses",encodeCourses(courses));o.put("monday",termSet()?monday().toString():JSONObject.NULL);o.put("totalWeeks",totalWeeks());o.put("termName",termName());o.put("times",new JSONArray(Arrays.asList(times())));return o;}
    void restore(JSONObject o) throws JSONException {
        if(!"kejian/1".equals(o.optString("format")))throw new IllegalArgumentException("请选择课间导出的备份文件");
        List<Course> list=readCourses(o.getJSONArray("courses"));String date=o.isNull("monday")?null:o.getString("monday");if(date!=null && LocalDate.parse(date).getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("学期起始日需为周一");
        int total=strictInt(o.get("totalWeeks"));if(total<1||total>53)throw new IllegalArgumentException("学期周数无效");for(Course c:list)if(c.weeks.last()>total)throw new IllegalArgumentException("备份中的课程周次超出学期范围");
        JSONArray times=o.getJSONArray("times");String[] t=new String[times.length()];for(int i=0;i<t.length;i++)t[i]=times.getString(i);validateTimes(t);
        SharedPreferences.Editor edit=prefs.edit().putString("courses",encodeCourses(list).toString()).putInt("totalWeeks",total).putString("termName",o.optString("termName","我的学期")).putString("times",String.join("\n",t)).putBoolean("timesConfirmed",false);if(date==null)edit.remove("monday");else edit.putString("monday",date);
        if(!edit.commit())throw new IllegalStateException("恢复失败，请检查手机存储空间");courses.clear();courses.addAll(list);loadFailed=false;
    }
    static void validateTimes(String[] t) {
        if(t.length<1||t.length>16)throw new IllegalArgumentException("请填写 1–16 节课的时间，每行一节");
        LocalTime prev=null;
        for(String s:t){if(!s.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}"))throw new IllegalArgumentException("时间格式：08:00-08:45，每行一节");LocalTime a=LocalTime.parse(s.substring(0,5)),b=LocalTime.parse(s.substring(6));if(!b.isAfter(a)||(prev!=null&&a.isBefore(prev)))throw new IllegalArgumentException("结束需晚于开始，各节课时间不能重叠");prev=b;}
    }
}
