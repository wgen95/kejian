package cn.kejian.timetable;
import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

public class StoreTest {
    static int checks=0;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    interface Checked { void run() throws Exception; }
    static void reject(Checked r) throws Exception {try{r.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected){checks++;}}
    static final class Memory {
        final Map<String,Object> values=new HashMap<>();boolean fail=false;
        final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
            switch(m.getName()){
                case "getString":case "getInt":case "getBoolean":return values.getOrDefault(a[0],a[1]);
                case "contains":return values.containsKey(a[0]);
                case "edit":Map<String,Object> pending=new HashMap<>(values);return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(proxy,method,args)->{
                    if(method.getName().startsWith("put")){pending.put((String)args[0],args[1]);return proxy;}
                    if(method.getName().equals("remove")){pending.remove(args[0]);return proxy;}
                    if(method.getName().equals("commit")||method.getName().equals("apply")){if(!fail){values.clear();values.putAll(pending);}return method.getName().equals("commit")?!fail:null;}
                    throw new UnsupportedOperationException(method.getName());
                });
                default:throw new UnsupportedOperationException(m.getName());
            }
        });
    }
    static Course course(){Course c=new Course();c.name="高等数学";c.room="教学楼101";c.weeks.addAll(Course.parseWeeks("1-16单"));return c;}
    public static void main(String[] args) throws Exception {
        Memory memory=new Memory();Store store=new Store(memory.prefs);check(store.courses.isEmpty()&&!store.loadFailed,"first launch empty");
        store.courses.add(course());store.save();Store reopened=new Store(memory.prefs);check(reopened.courses.size()==1,"saved course survives reopening");check(reopened.courses.get(0).weeks.equals(store.courses.get(0).weeks),"irregular weeks preserved");
        memory.values.put("monday","2026-09-07");memory.values.put("totalWeeks",20);memory.values.put("termName","秋季学期");
        JSONObject backup=store.backup();Memory restoredMemory=new Memory();Store restored=new Store(restoredMemory.prefs);restored.restore(backup);check(restored.courses.get(0).fingerprint().equals(store.courses.get(0).fingerprint()),"backup course round trip");check(restored.monday().equals(LocalDate.of(2026,9,7)),"backup semester date");check(!restoredMemory.values.get("timesConfirmed").equals(true),"restored timing requires recheck");
        JSONObject decimal=Store.encode(course());decimal.put("start",1.5);reject(()->Store.readCourses(new JSONArray().put(decimal)));
        JSONObject booleanDay=Store.encode(course());booleanDay.put("day",true);reject(()->Store.readCourses(new JSONArray().put(booleanDay)));
        JSONObject badWeeks=Store.encode(course());badWeeks.put("weeks",new JSONArray().put(1.5));reject(()->Store.readCourses(new JSONArray().put(badWeeks)));
        Course c=course();JSONArray duplicates=new JSONArray().put(Store.encode(c)).put(Store.encode(c));List<Course> ids=Store.readCourses(duplicates);check(!ids.get(0).id.equals(ids.get(1).id),"duplicate IDs repaired");
        reject(()->Store.validateTimes(new String[]{"08:00-08:45","08:30-09:15"}));
        reject(()->Store.validateTimes(new String[]{"08:45-08:00"}));reject(()->Store.validateTimes(new String[]{}));reject(()->Store.validateTimes(new String[]{"8:00-8:45"}));Store.validateTimes(Store.DEFAULT_TIMES);checks++;
        String before=restored.backup().toString();JSONObject wrongMonday=new JSONObject(backup.toString());wrongMonday.put("monday","2026-09-08");reject(()->restored.restore(wrongMonday));check(restored.backup().toString().equals(before),"invalid restore leaves current data unchanged");
        JSONObject shortTerm=new JSONObject(backup.toString());shortTerm.put("totalWeeks",2);reject(()->restored.restore(shortTerm));
        restoredMemory.fail=true;try{restored.restore(backup);throw new AssertionError("failed disk write accepted");}catch(IllegalStateException expected){checks++;}check(restored.courses.size()==1,"disk failure preserves in-memory courses");
        Memory corrupted=new Memory();corrupted.values.put("courses","not json");Store broken=new Store(corrupted.prefs);check(broken.loadFailed,"invalid stored data reported");check(corrupted.values.get("courses").equals("not json"),"invalid stored data retained");
        Memory extended=new Memory();Store extendedStore=new Store(extended.prefs);Course longCourse=course();longCourse.weeks.add(30);extendedStore.courses.add(longCourse);extendedStore.save();check(extendedStore.totalWeeks()==30,"long schedule extends visible semester");
        System.out.println("Store tests passed: "+checks);
    }
}
