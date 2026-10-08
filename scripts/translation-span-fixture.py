"""Minimal span-preserving CharSequence for JVM lifecycle tests without Android runtime."""
SPAN_BUILDER = r'''package android.text;
import java.util.*;
public class SpannableStringBuilder implements CharSequence {
 public static class Mark {public int start,end;public String id;Mark(int s,int e,String i){start=s;end=e;id=i;}}
 private final StringBuilder text=new StringBuilder(); public final List<Mark> marks=new ArrayList<>();
 public SpannableStringBuilder(CharSequence value){append(value);}
 public SpannableStringBuilder append(CharSequence value){int shift=length();text.append(value);if(value instanceof SpannableStringBuilder)for(Mark m:((SpannableStringBuilder)value).marks)marks.add(new Mark(m.start+shift,m.end+shift,m.id));return this;}
 public SpannableStringBuilder mark(int start,int end,String id){marks.add(new Mark(start,end,id));return this;}
 public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}public String toString(){return text.toString();}
 public CharSequence subSequence(int start,int end){var r=new SpannableStringBuilder(text.substring(start,end));for(Mark m:marks)if(m.end>start&&m.start<end)r.mark(Math.max(m.start,start)-start,Math.min(m.end,end)-start,m.id);return r;}
 public String signature(){StringBuilder r=new StringBuilder();for(Mark m:marks)r.append(m.start).append(':').append(m.end).append(':').append(m.id).append(';');return r.toString();}
}
'''
