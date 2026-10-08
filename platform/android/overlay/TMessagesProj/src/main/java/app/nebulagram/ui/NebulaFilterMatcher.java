package app.nebulagram.ui;

import java.text.Normalizer;
import java.util.Locale;

/** Literal, Unicode-aware matching; user text is never evaluated as a regular expression. */
public final class NebulaFilterMatcher {
    private static final String CYR="абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LAT={"a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","sch","","y","","e","yu","ya"};
    private NebulaFilterMatcher() { }
    public static String normalize(String text, boolean translit) {
        String value=Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if(!translit)return value;
        StringBuilder result=new StringBuilder(value.length());
        for(int offset=0;offset<value.length();){int cp=value.codePointAt(offset);offset+=Character.charCount(cp);int index=CYR.indexOf(cp);if(index>=0)result.append(LAT[index]);else result.appendCodePoint(cp);}
        return result.toString();
    }
    private static boolean word(int cp){return Character.isLetterOrDigit(cp)||cp=='_'||Character.getType(cp)==Character.NON_SPACING_MARK;}
    public static boolean matches(String text,String[] rules,boolean translit,boolean whole){
        String haystack=normalize(text,translit);
        for(String rule:rules){String needle=normalize(rule.trim(),translit);if(needle.isEmpty())continue;
            int start=0,index;while((index=haystack.indexOf(needle,start))>=0){int end=index+needle.length();
                if(!whole||(index==0||!word(haystack.codePointBefore(index)))&&(end==haystack.length()||!word(haystack.codePointAt(end))))return true;
                start=index+1;
            }
        }
        return false;
    }
}
