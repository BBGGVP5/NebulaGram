package app.nebulagram.ui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Locale;

/** Bounded arithmetic only: no eval, code, network, or mutations of the user's draft. */
public final class NebulaMath {
    private static final MathContext CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final MathContext WIDE = new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal LIMIT = new BigDecimal("1e100");
    /** U+202F between digit groups: printed by group(), skipped when read back. */
    public static final char GROUP = ' ';
    private final String input;
    private final boolean degrees;
    private int position, operations, depth, arguments;
    private boolean percent;
    private NebulaMath(String input, boolean degrees) { this.input=input; this.degrees=degrees; }

    public static String result(String text) { return result(text, true, false); }
    /** Without a trailing "=" only plain digit arithmetic is answered, so phone numbers, IDs and dates stay quiet. */
    public static String result(String text, boolean degrees, boolean group) {
        if(text==null||text.length()>120)return "";
        String s=normalize(text);
        boolean marked=s.endsWith("=");if(marked)s=s.substring(0,s.length()-1).trim();
        if(!marked&&!(s.contains("+")&&!s.startsWith("+"))&&!s.matches(".*[*/%()^].*")&&!s.matches(".*\\s[+-]\\s.*"))return "";
        if(!marked&&!s.matches("[0-9.,\\s+*/%()^-]+"))return "";
        if(!marked)s=s.replace(',','.');
        String radix=radixCall(s, degrees);if(radix!=null)return radix;
        NebulaMath parser=new NebulaMath(s, degrees);BigDecimal value=parser.evaluate();
        if(value==null||parser.operations==0)return "";
        return format(value, group);
    }
    /** An amount for the converters: "5k", "2*50", "1,5". Null when it is not a complete expression. */
    public static BigDecimal value(String text, boolean degrees) {
        if(text==null||text.length()>120)return null;
        String s=normalize(text);if(s.isEmpty())return null;
        return new NebulaMath(s, degrees).evaluate();
    }
    public static String format(BigDecimal value, boolean group) {
        BigDecimal rounded=value.round(CONTEXT).stripTrailingZeros();
        if(rounded.signum()==0)return "0";
        String plain=rounded.toPlainString();
        if(plain.replace("-","").replace(".","").length()>18)return rounded.toString().replace("E+","e").replace("E","e");
        return group?group(plain):plain;
    }
    static String group(String plain) {
        int start=plain.startsWith("-")?1:0,dot=plain.indexOf('.');int end=dot<0?plain.length():dot;
        if(end-start<5)return plain;
        StringBuilder out=new StringBuilder(plain.substring(0,start));
        for(int i=start;i<end;i++){if(i>start&&(end-i)%3==0)out.append(GROUP);out.append(plain.charAt(i));}
        return out.append(plain.substring(end)).toString();
    }
    private static String normalize(String text) {
        return text.trim().replace('×','*').replace('·','*').replace('÷','/').replace('−','-').replace('–','-').replace(' ',GROUP);
    }
    /** hex(255), bin(5), oct(8): a whole-integer result printed in that base. */
    private static String radixCall(String s, boolean degrees) {
        String lower=s.toLowerCase(Locale.ROOT);int base;String prefix;
        if(lower.startsWith("hex("))base=16;else if(lower.startsWith("bin("))base=2;else if(lower.startsWith("oct("))base=8;else return null;
        if(!lower.endsWith(")"))return "";
        prefix=base==16?"0x":base==2?"0b":"0o";
        BigDecimal value=new NebulaMath(s.substring(4,s.length()-1), degrees).evaluate();
        if(value==null)return "";
        try{BigInteger whole=value.toBigIntegerExact();String digits=whole.abs().toString(base);if(base==16)digits=digits.toUpperCase(Locale.ROOT);
            return digits.length()>40?"":(whole.signum()<0?"-":"")+prefix+digits;}catch(ArithmeticException notWhole){return "";}
    }
    private BigDecimal evaluate() {
        try{BigDecimal value=expression();spaces();
            if(position!=input.length()||value.abs().compareTo(LIMIT)>0)return null;return value;
        }catch(RuntimeException ignored){return null;}
    }
    private void spaces(){while(position<input.length()&&Character.isWhitespace(input.charAt(position)))position++;}
    private boolean take(char value){spaces();if(position<input.length()&&input.charAt(position)==value){position++;return true;}return false;}
    private boolean word(String value){spaces();int end=position+value.length();
        if(end>input.length()||!input.regionMatches(true,position,value,0,value.length()))return false;
        if(end<input.length()&&Character.isLetter(input.charAt(end)))return false;position=end;return true;}
    private char peek(){spaces();return position<input.length()?input.charAt(position):'\0';}
    private void operation(){if(++operations>60)throw new IllegalArgumentException();}
    private BigDecimal expression(){
        BigDecimal value=term();
        while(true){
            boolean plus;if(take('+'))plus=true;else if(take('-'))plus=false;else return value;
            operation();BigDecimal right=term();
            // "200 + 15%" is 230, the way people say it; "200 + 15% * 2" stays plain arithmetic.
            if(percent)right=value.multiply(right,WIDE);
            value=plus?value.add(right,WIDE):value.subtract(right,WIDE);
        }
    }
    private BigDecimal term(){
        BigDecimal value=unary();int factors=1;boolean bare=percent;
        while(true){
            if(take('*')||word("of")||word("от")){operation();value=value.multiply(unary(),WIDE);}
            else if(take('/')){operation();BigDecimal d=unary();if(d.signum()==0)throw new ArithmeticException();value=value.divide(d,WIDE);}
            else if(word("mod")){operation();BigDecimal d=unary();if(d.signum()==0)throw new ArithmeticException();value=value.remainder(d,WIDE);}
            else if(implicit()){operation();value=value.multiply(unary(),WIDE);}
            else{percent=factors==1&&bare;return value;}
            factors++;
        }
    }
    /** "2pi", "3(4+1)", "2sqrt(9)". */
    private boolean implicit(){char c=peek();if(c=='(')return true;if(!Character.isLetter(c)&&c!='π')return false;
        int save=position;String name=identifier();position=save;
        return name.equals("pi")||name.equals("π")||name.equals("пи")||name.equals("e")||function(name);}
    private BigDecimal unary(){
        if(++depth>16)throw new IllegalArgumentException();BigDecimal value;
        if(take('-'))value=unary().negate();else if(take('+'))value=unary();else value=power();
        depth--;return value;
    }
    private BigDecimal power(){
        BigDecimal base=postfix();boolean wasPercent=percent;
        if(!take('^')){percent=wasPercent;return base;}
        operation();BigDecimal exponent=unary();percent=false;
        if(exponent.signum()==0)return BigDecimal.ONE;
        if(exponent.stripTrailingZeros().scale()<=0&&exponent.abs().compareTo(BigDecimal.valueOf(400))<=0){
            int n=exponent.intValueExact();BigDecimal raised=base.pow(Math.abs(n),WIDE);
            if(n<0){if(raised.signum()==0)throw new ArithmeticException();raised=BigDecimal.ONE.divide(raised,WIDE);}return raised;
        }
        return real(Math.pow(base.doubleValue(),exponent.doubleValue()));
    }
    private BigDecimal postfix(){
        BigDecimal value=primary();percent=false;
        if(take('%')){operation();percent=true;value=value.divide(BigDecimal.valueOf(100),WIDE);}
        return value;
    }
    private BigDecimal primary(){
        char c=peek();
        if(c=='('){position++;int saved=arguments;arguments=0;BigDecimal v=expression();arguments=saved;if(!take(')'))throw new IllegalArgumentException();return v;}
        if(Character.isDigit(c)||c=='.')return number();
        String name=identifier();
        if(name.equals("pi")||name.equals("π")||name.equals("пи")){operation();return new BigDecimal("3.14159265358979323846");}
        if(name.equals("e")){operation();return new BigDecimal("2.71828182845904523536");}
        if(!function(name)||!take('('))throw new IllegalArgumentException();
        operation();
        if(name.equals("dec"))return decimal();
        ArrayList<BigDecimal> args=new ArrayList<>();int saved=arguments;arguments++;
        do{args.add(expression());}while(take(',')||take(';'));
        arguments=saved;if(!take(')'))throw new IllegalArgumentException();
        return call(name,args);
    }
    private String identifier(){spaces();int start=position;
        while(position<input.length()&&(Character.isLetter(input.charAt(position))||input.charAt(position)=='π'||(position>start&&Character.isDigit(input.charAt(position)))))position++;
        if(position==start)throw new IllegalArgumentException();return input.substring(start,position).toLowerCase(Locale.ROOT);}
    private static boolean function(String name){
        switch(name){case "round":case "floor":case "ceil":case "min":case "max":case "abs":case "sqrt":case "cbrt":case "log":case "lg":case "ln":case "log2":
            case "sin":case "cos":case "tan":case "tg":case "asin":case "acos":case "atan":case "exp":case "dec":case "hex":case "bin":case "oct":return true;default:return false;}
    }
    private BigDecimal call(String name, ArrayList<BigDecimal> args){
        BigDecimal x=args.get(0);int count=args.size();
        if(name.equals("min")||name.equals("max")){BigDecimal r=x;for(BigDecimal a:args)r=name.equals("min")?r.min(a):r.max(a);return r;}
        if(name.equals("round")){if(count>2)throw new IllegalArgumentException();int places=count==2?args.get(1).intValueExact():0;
            if(places<-20||places>20)throw new IllegalArgumentException();return x.setScale(places,RoundingMode.HALF_UP);}
        if(name.equals("log")&&count==2){double b=args.get(1).doubleValue();return real(Math.log(x.doubleValue())/Math.log(b));}
        if(count!=1)throw new IllegalArgumentException();
        double d=x.doubleValue(),angle=degrees?Math.toRadians(d):d;
        switch(name){
            case "floor":return x.setScale(0,RoundingMode.FLOOR);
            case "ceil":return x.setScale(0,RoundingMode.CEILING);
            case "abs":return x.abs();
            case "hex":case "bin":case "oct":return x;
            case "sqrt":if(x.signum()<0)throw new ArithmeticException();return real(Math.sqrt(d));
            case "cbrt":return real(Math.cbrt(d));
            case "log":case "lg":return positive(x,Math.log10(d));
            case "ln":return positive(x,Math.log(d));
            case "log2":return positive(x,Math.log(d)/Math.log(2));
            case "exp":return real(Math.exp(d));
            case "sin":return trig(Math.sin(angle));
            case "cos":return trig(Math.cos(angle));
            case "tan":case "tg":if(Math.abs(Math.cos(angle))<1e-12)throw new ArithmeticException();return trig(Math.tan(angle));
            case "asin":return inverse(Math.asin(d));
            case "acos":return inverse(Math.acos(d));
            default:return inverse(Math.atan(d));
        }
    }
    /** dec(ff, 16), dec(101, 2), dec(0x1F): digits in base n, back to decimal. */
    private BigDecimal decimal(){
        spaces();int start=position;while(position<input.length()&&Character.isLetterOrDigit(input.charAt(position)))position++;
        String digits=input.substring(start,position).toLowerCase(Locale.ROOT);int base=10;
        if(take(',')||take(';')){BigDecimal b=expression();base=b.intValueExact();}
        else if(digits.length()>2&&digits.charAt(0)=='0'&&"xbo".indexOf(digits.charAt(1))>=0){base=digits.charAt(1)=='x'?16:digits.charAt(1)=='b'?2:8;digits=digits.substring(2);}
        if(!take(')')||digits.isEmpty()||digits.length()>64||base<2||base>36)throw new IllegalArgumentException();
        return new BigDecimal(new BigInteger(digits,base));
    }
    private BigDecimal positive(BigDecimal x,double value){if(x.signum()<=0)throw new ArithmeticException();return real(value);}
    private BigDecimal trig(double value){return real(Math.abs(value)<1e-12?0:value);}
    private BigDecimal inverse(double value){return real(degrees?Math.toDegrees(value):value);}
    private static BigDecimal real(double value){if(Double.isNaN(value)||Double.isInfinite(value))throw new ArithmeticException();return new BigDecimal(value,CONTEXT);}
    private BigDecimal number(){
        spaces();int start=position;StringBuilder digits=new StringBuilder();
        if(input.startsWith("0x",position)||input.startsWith("0X",position)||input.startsWith("0b",position)||input.startsWith("0o",position)){
            char kind=Character.toLowerCase(input.charAt(position+1));int base=kind=='x'?16:kind=='b'?2:8;position+=2;
            int from=position;while(position<input.length()&&Character.digit(input.charAt(position),base)>=0)position++;
            if(position==from||position-from>40||(position<input.length()&&Character.isLetterOrDigit(input.charAt(position))))throw new IllegalArgumentException();
            operation();return new BigDecimal(new BigInteger(input.substring(from,position),base));
        }
        while(position<input.length()){
            char c=input.charAt(position);
            if(Character.isDigit(c)||c=='.')digits.append(c);
            else if(c==GROUP&&position+1<input.length()&&Character.isDigit(input.charAt(position+1))&&digits.length()>0){position++;continue;}
            else if(c==','&&arguments==0&&position+1<input.length()&&Character.isDigit(input.charAt(position+1)))digits.append('.');
            else break;
            position++;
        }
        if(position-start>40||digits.length()==0)throw new IllegalArgumentException();
        BigDecimal value=new BigDecimal(digits.toString());
        // Shorthands: 5k, 2m, 3.5млн. A unit such as "5 km" or "2 min" is left for the converters.
        int save=position;
        if(position<input.length()&&Character.isLetter(input.charAt(position))){
            int end=position;while(end<input.length()&&Character.isLetter(input.charAt(end)))end++;
            String suffix=input.substring(position,end);int exponent=0;
            switch(suffix){case "k":case "K":case "к":case "К":case "тыс":exponent=3;break;case "m":case "M":case "млн":exponent=6;break;case "bn":case "B":case "млрд":exponent=9;break;default:break;}
            if(exponent>0){position=end;operation();return value.scaleByPowerOfTen(exponent);}
            position=save;
        }
        return value;
    }
}
