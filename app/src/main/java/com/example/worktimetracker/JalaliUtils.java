package com.example.worktimetracker;

import java.util.Calendar;
import java.util.Locale;

final class JalaliUtils {
    static class JalaliDate { final int year, month, day; JalaliDate(int y, int m, int d){ year=y; month=m; day=d; } }
    private JalaliUtils() {}

    static JalaliDate fromMillis(long millis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH));
    }
    static String dateString(long millis) { JalaliDate j=fromMillis(millis); return String.format(Locale.US, "%04d/%02d/%02d", j.year,j.month,j.day); }
    static String monthLabel(long millis) { JalaliDate j=fromMillis(millis); return String.format(Locale.US, "%04d/%02d", j.year,j.month); }
    static String fullDate(long millis) { return weekdayName(millis) + "، " + dateString(millis); }
    static String weekdayName(long millis) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(millis);
        switch(c.get(Calendar.DAY_OF_WEEK)){case Calendar.SATURDAY:return"شنبه";case Calendar.SUNDAY:return"یکشنبه";case Calendar.MONDAY:return"دوشنبه";case Calendar.TUESDAY:return"سه‌شنبه";case Calendar.WEDNESDAY:return"چهارشنبه";case Calendar.THURSDAY:return"پنجشنبه";default:return"جمعه";}
    }
    static long monthStartMillis(long millis) {
        JalaliDate j=fromMillis(millis); int[] g=jalaliToGregorian(j.year,j.month,1);
        Calendar c=Calendar.getInstance(); c.set(g[0], g[1]-1, g[2],0,0,0); c.set(Calendar.MILLISECOND,0); return c.getTimeInMillis();
    }
    static long parseDateToMillis(String text) {
        String[] p=(text==null?"":text.trim().replace('-','/')).split("/");
        if(p.length!=3) throw new IllegalArgumentException("فرمت تاریخ باید مثل 1405/04/01 باشد.");
        int[] g=jalaliToGregorian(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));
        Calendar c=Calendar.getInstance(); c.set(g[0],g[1]-1,g[2],0,0,0); c.set(Calendar.MILLISECOND,0); return c.getTimeInMillis();
    }
    static JalaliDate gregorianToJalali(int gy,int gm,int gd){int[]gdm={0,31,59,90,120,151,181,212,243,273,304,334};int jy;if(gy>1600){jy=979;gy-=1600;}else{jy=0;gy-=621;}int gy2=(gm>2)?gy+1:gy;int days=365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400-80+gd+gdm[gm-1];jy+=33*(days/12053);days%=12053;jy+=4*(days/1461);days%=1461;if(days>365){jy+=(days-1)/365;days=(days-1)%365;}int jm,jd;if(days<186){jm=1+days/31;jd=1+days%31;}else{jm=7+(days-186)/30;jd=1+(days-186)%30;}return new JalaliDate(jy,jm,jd);} 
    static int[] jalaliToGregorian(int jy,int jm,int jd){if(jm<1||jm>12||jd<1||jd>31||(jm>6&&jd>30))throw new IllegalArgumentException();int gy;if(jy>979){gy=1600;jy-=979;}else{gy=621;}int days=365*jy+(jy/33)*8+((jy%33)+3)/4+78+jd;if(jm<7)days+=(jm-1)*31;else days+=(jm-7)*30+186;gy+=400*(days/146097);days%=146097;if(days>36524){gy+=100*(--days/36524);days%=36524;if(days>=365)days++;}gy+=4*(days/1461);days%=1461;if(days>365){gy+=(days-1)/365;days=(days-1)%365;}int gd=days+1;int[]sal={0,31,((gy%4==0&&gy%100!=0)||(gy%400==0))?29:28,31,30,31,30,31,31,30,31,30,31};int gm;for(gm=1;gm<=12&&gd>sal[gm];gm++)gd-=sal[gm];return new int[]{gy,gm,gd};}
}
