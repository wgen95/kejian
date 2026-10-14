package cn.kejian.timetable;

import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

public final class Course {
    public String id = UUID.randomUUID().toString();
    public String name = "", room = "", teacher = "", note = "";
    public int day = 1, start = 1, end = 2, color = 0;
    public final SortedSet<Integer> weeks = new TreeSet<>();
    public Course copy() {
        Course c = new Course(); c.id=id; c.name=name; c.room=room; c.teacher=teacher; c.note=note;
        c.day=day; c.start=start; c.end=end; c.color=color; c.weeks.addAll(weeks); return c;
    }
    public void validate() {
        if (name.trim().isEmpty() || name.length()>120) throw new IllegalArgumentException("请填写课程名称（最多 120 字）");
        if (day<1 || day>7 || start<1 || end<start || end>16) throw new IllegalArgumentException("请检查星期和节次（1–16 节）");
        if (weeks.isEmpty() || weeks.first()<1 || weeks.last()>53) throw new IllegalArgumentException("周次需在 1–53 周之间");
        if(room.length()>300 || teacher.length()>120 || note.length()>2000) throw new IllegalArgumentException("课程备注或地点过长");
        if(color<0 || color>5) throw new IllegalArgumentException("课程颜色无效");
    }
    public boolean clashes(Course c) { return day==c.day && start<=c.end && end>=c.start && !Collections.disjoint(weeks,c.weeks); }
    public String fingerprint() { return name.trim()+"|"+room.trim()+"|"+teacher.trim()+"|"+day+"|"+start+"|"+end+"|"+weeks; }
    public static SortedSet<Integer> parseWeeks(String text) {
        SortedSet<Integer> result=new TreeSet<>();
        String s=text.trim().replace('，',',').replace('、',',').replace('～','-').replace('—','-').replace('–','-').replace('~','-').replace("周", "").replace("第", "").replaceAll("\\s+", "");
        int parity=s.contains("单")?1:s.contains("双")?2:0;
        s=s.replaceAll("[单双()（）]", "");
        if (!s.matches("\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*")) throw new IllegalArgumentException("周次格式：1-16、1-16单，或 1-8,10-16");
        for(String p:s.split(",")) {
            String[] range=p.split("-"); int a=Integer.parseInt(range[0]), b=range.length==2?Integer.parseInt(range[1]):a;
            if(a<1 || b>53 || a>b) throw new IllegalArgumentException("周次需在 1–53 周之间，起始不能大于结束");
            for(int i=a;i<=b;i++) if(parity==0 || (parity==1 && i%2==1) || (parity==2 && i%2==0)) result.add(i);
        }
        if(result.isEmpty()) throw new IllegalArgumentException("所填周次没有符合的上课周");
        return result;
    }
    public static String weeksText(Collection<Integer> values) {
        List<Integer> a=new ArrayList<>(new TreeSet<>(values)); StringBuilder s=new StringBuilder();
        for(int i=0;i<a.size();i++){int first=a.get(i),last=first;while(i+1<a.size() && a.get(i+1)==last+1)last=a.get(++i);if(s.length()>0)s.append(',');s.append(first);if(last!=first)s.append('-').append(last);} return s.toString();
    }
    public static int weekAt(LocalDate monday, LocalDate day) { return (int)Math.floorDiv(ChronoUnit.DAYS.between(monday, day),7)+1; }
}
