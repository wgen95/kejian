package cn.kejian.timetable;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.util.*;

// Real TextViews retain TalkBack, keyboard focus and system font scaling support.
final class TimetableView extends HorizontalScrollView {
    TimetableView(MainActivity a,Store store,int week) {
        super(a);setFillViewport(true);setHorizontalScrollBarEnabled(false);
        boolean weekend=store.prefs.getBoolean("weekend",true);int days=weekend?7:5;
        int usable=a.getResources().getDisplayMetrics().widthPixels-Ui.dp(a,48);
        int col=Math.max(Ui.dp(a,62), (usable-Ui.dp(a,34))/days), row=Ui.dp(a,56);
        String[] times=store.times();int periods=Math.max(12,times.length);for(Course c:store.courses)periods=Math.max(periods,c.end);
        LinearLayout table=Ui.row(a);table.setGravity(Gravity.TOP);addView(table,new LayoutParams(-2,-1));
        LinearLayout gutter=Ui.column(a);table.addView(gutter,new LinearLayout.LayoutParams(Ui.dp(a,34),-1));
        TextView month=Ui.text(a,store.monday().plusWeeks(week-1).getMonthValue()+"月",11,Ui.MUTED,false);month.setGravity(Gravity.CENTER);gutter.addView(month,new LinearLayout.LayoutParams(-1,Ui.dp(a,54)));
        float font=a.getResources().getConfiguration().fontScale;
        row=(int)(row*Math.max(1f,font));
        for(int p=1;p<=periods;p++){TextView t=Ui.text(a,String.format(java.util.Locale.CHINA,"%02d",p)+(p<=times.length?"\n"+times[p-1].substring(0,5):""),10,Ui.MUTED,false);t.setGravity(Gravity.CENTER);gutter.addView(t,new LinearLayout.LayoutParams(-1,row));}
        for(int day=1;day<=days;day++) {
            final int weekday=day;LinearLayout column=Ui.column(a);table.addView(column,new LinearLayout.LayoutParams(col,-1));
            LocalDate date=store.monday().plusWeeks(week-1).plusDays(day-1);boolean today=date.equals(LocalDate.now());
            TextView header=Ui.text(a,"周"+"一二三四五六日".charAt(day-1)+"\n"+date.getDayOfMonth(),12,today?Color.WHITE:Ui.MUTED,today);header.setGravity(Gravity.CENTER);if(today)header.setBackground(Ui.shape(Ui.GREEN,Ui.dp(a,12)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,Ui.dp(a,48));hp.setMargins(Ui.dp(a,2),0,Ui.dp(a,2),Ui.dp(a,6));column.addView(header,hp);
            FrameLayout body=new FrameLayout(a);column.addView(body,new LinearLayout.LayoutParams(-1,periods*row));
            for(int p=1;p<=periods;p++){final int section=p;TextView slot=Ui.text(a,"",12,Ui.MUTED,false);slot.setContentDescription("周"+"一二三四五六日".charAt(day-1)+"第"+p+"节，添加课程");slot.setBackground(Ui.shape(0xFFF0F2ED,Ui.dp(a,10)));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,row-Ui.dp(a,4));sp.topMargin=(p-1)*row;sp.leftMargin=sp.rightMargin=Ui.dp(a,2);body.addView(slot,sp);slot.setOnClickListener(v->{Course c=new Course();c.day=weekday;c.start=section;c.end=Math.min(16,section+1);c.weeks.addAll(Course.parseWeeks("1-"+store.totalWeeks()));a.editCourse(c,false);});}
            List<Course> visible=new ArrayList<>();for(Course c:store.courses)if(c.day==day&&c.weeks.contains(week))visible.add(c);visible.sort(Comparator.comparingInt(c->c.start));
            // Connected overlap groups share the day column, so no class hides another.
            for(int i=0;i<visible.size();) {
                int j=i+1,maxEnd=visible.get(i).end;while(j<visible.size()&&visible.get(j).start<=maxEnd){maxEnd=Math.max(maxEnd,visible.get(j).end);j++;}
                int count=j-i;
                if(count>2){final List<Course> group=new ArrayList<>(visible.subList(i,j));TextView combined=Ui.text(a,count+" 门课重叠\n点击查看",12,Ui.INK,true);combined.setGravity(Gravity.CENTER);combined.setBackground(Ui.shape(Ui.COLORS[4],Ui.dp(a,10)));combined.setContentDescription(count+"门课程在同一天有重叠，点击查看列表");FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(col-Ui.dp(a,4),(maxEnd-visible.get(i).start+1)*row-Ui.dp(a,4));gp.leftMargin=Ui.dp(a,2);gp.topMargin=(visible.get(i).start-1)*row;body.addView(combined,gp);combined.setOnClickListener(v->{String[] names=new String[group.size()];for(int k=0;k<names.length;k++)names[k]=group.get(k).name+" · 第"+group.get(k).start+"–"+group.get(k).end+"节";new android.app.AlertDialog.Builder(a).setTitle("重叠的课程").setItems(names,(d,n)->a.showCourse(group.get(n))).setNegativeButton("关闭",null).show();});i=j;continue;}
                for(int k=i;k<j;k++){Course c=visible.get(k);TextView card=Ui.text(a,c.name+(c.room.isEmpty()?"":"\n"+c.room),12,Ui.INK,true);card.setGravity(Gravity.TOP);card.setPadding(Ui.dp(a,6),Ui.dp(a,8),Ui.dp(a,4),Ui.dp(a,4));card.setMaxLines(Math.max(2,(c.end-c.start+1)*3));card.setEllipsize(android.text.TextUtils.TruncateAt.END);card.setBackground(Ui.shape(Ui.COLORS[c.color],Ui.dp(a,10)));card.setContentDescription(c.name+"，"+c.room+"，第"+c.start+"至"+c.end+"节"+(count>1?"，有时间重叠":""));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams((col-Ui.dp(a,4))/count-Ui.dp(a,2),(c.end-c.start+1)*row-Ui.dp(a,4));cp.leftMargin=Ui.dp(a,2)+(k-i)*(col-Ui.dp(a,4))/count;cp.topMargin=(c.start-1)*row;body.addView(card,cp);card.setOnClickListener(v->a.showCourse(c));}i=j;
            }
        }
    }
}
