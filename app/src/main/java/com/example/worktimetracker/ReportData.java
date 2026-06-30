package com.example.worktimetracker;
import java.util.ArrayList;import java.util.List;
class ReportData { long totalSeconds; final List<TopItem> topActivities=new ArrayList<>(); final List<DailyItem> dailyRows=new ArrayList<>(); static class TopItem{String app,category;long seconds;int percent;} static class DailyItem{long dayStartMs,total;String jalaliDate,weekday,start,end,topApp;} }
