import cn.kejian.timetable.Course;
import java.time.LocalDate;
import java.util.*;

public class CourseTest {
    static int checks=0;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void invalid(String text){try{Course.parseWeeks(text);throw new AssertionError("accepted invalid weeks: "+text);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        check(Course.parseWeeks("1-16单").equals(new TreeSet<>(Arrays.asList(1,3,5,7,9,11,13,15))),"odd weeks");
        check(Course.parseWeeks("第2～8周（双）").equals(new TreeSet<>(Arrays.asList(2,4,6,8))),"even weeks and localized input");
        check(Course.parseWeeks("1-3，5、7-8").equals(new TreeSet<>(Arrays.asList(1,2,3,5,7,8))),"irregular weeks");
        check(Course.weeksText(Course.parseWeeks("1-3,5,7-8")).equals("1-3,5,7-8"),"compression round trip");
        for(String s:Arrays.asList("", "0-12", "1-54", "16-1", "abc", "1-2-3", "2单", "1,", "1e3"))invalid(s);
        Course a=new Course();a.name="数学";a.weeks.addAll(Course.parseWeeks("1-16单"));Course b=a.copy();b.id="b";b.weeks.clear();b.weeks.addAll(Course.parseWeeks("2-16双"));
        check(!a.clashes(b),"same slot alternating weeks is not conflict");b.weeks.add(3);check(a.clashes(b),"shared week conflict");b.day=7;check(!a.clashes(b),"different day");b.day=1;b.start=3;b.end=4;check(!a.clashes(b),"adjacent nonoverlapping sections");
        Course copy=a.copy();copy.weeks.clear();check(!a.weeks.isEmpty(),"editing clone does not modify original");
        check(Course.weekAt(LocalDate.of(2026,9,7),LocalDate.of(2026,9,6))==0,"before term uses floor division");
        check(Course.weekAt(LocalDate.of(2026,9,7),LocalDate.of(2026,9,13))==1,"Sunday is same week");
        check(Course.weekAt(LocalDate.of(2026,9,7),LocalDate.of(2026,9,14))==2,"Monday increments week");
        check(Course.weekAt(LocalDate.of(2026,12,28),LocalDate.of(2027,1,4))==2,"new year boundary");
        a.validate();a.end=17;try{a.validate();throw new AssertionError("accepted 17 sections");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Course tests passed: "+checks);
    }
}
