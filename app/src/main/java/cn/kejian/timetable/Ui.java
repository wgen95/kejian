package cn.kejian.timetable;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(246,247,243),INK=Color.rgb(35,49,43),MUTED=Color.rgb(111,123,115),GREEN=Color.rgb(49,92,78);
    static final int[] COLORS={0xFFDDE9DD,0xFFE5E5F3,0xFFF5E6D5,0xFFDDEBF1,0xFFF0DFE2,0xFFE9EDCF};
    static int dp(android.content.Context c,float x){return (int)(x*c.getResources().getDisplayMetrics().density+0.5f);}
    static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static TextView text(Activity a,String s,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    static LinearLayout column(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static Button button(Activity a,String label,boolean primary,Runnable run){Button b=new Button(a);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(a,48));b.setMinimumWidth(0);b.setTextColor(primary?Color.WHITE:GREEN);b.setBackground(shape(primary?GREEN:0xFFE9EEE7,dp(a,14)));b.setPadding(dp(a,14),0,dp(a,14),0);b.setOnClickListener(v->run.run());return b;}
    static void gap(Activity a,LinearLayout v,int n){View x=new View(a);v.addView(x,new LinearLayout.LayoutParams(1,dp(a,n)));}
    static void install(Activity a,View root){a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(Build.VERSION.SDK_INT>=30)a.getWindow().setDecorFitsSystemWindows(false);else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);a.setContentView(root);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()),ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();}
    static EditText input(Activity a,LinearLayout box,String label,String value,int type){TextView title=text(a,label,12,MUTED,false);box.addView(title);EditText e=new EditText(a);e.setText(value);e.setTextColor(INK);e.setTextSize(16);e.setInputType(type);e.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));e.setBackground(shape(Color.WHITE,dp(a,10)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(a,6);p.bottomMargin=dp(a,14);box.addView(e,p);return e;}
}
