package app.nebulagram.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Bounded arithmetic only: no eval, names, code, network, or mutations of the user's draft. */
public final class NebulaMath {
    private static final MathContext CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private final String input;
    private int position, operations, depth;
    private NebulaMath(String input) { this.input=input; }
    public static String result(String text) {
        if(text==null||text.length()>120)return "";
        String s=text.trim().replace('×','*').replace('÷','/').replace(',','.');
        boolean marked=s.endsWith("=");if(marked)s=s.substring(0,s.length()-1).trim();
        // Do not interpret phone numbers, IDs and dates as subtraction.
        if(!marked&&!(s.contains("+")&&!s.startsWith("+"))&&!s.matches(".*[*/%()].*")&&!s.matches(".*\\s[+-]\\s.*"))return "";
        if(!s.matches("[0-9.\\s+*/%()-]+"))return "";
        try {
            NebulaMath parser=new NebulaMath(s);BigDecimal result=parser.expression();parser.spaces();
            if(parser.position!=s.length()||parser.operations==0)return "";
            String value=result.stripTrailingZeros().toPlainString();return value.length()<=24?value:"";
        } catch(RuntimeException ignored){return "";}
    }
    private void spaces(){while(position<input.length()&&Character.isWhitespace(input.charAt(position)))position++;}
    private boolean take(char value){spaces();if(position<input.length()&&input.charAt(position)==value){position++;return true;}return false;}
    private void operation(){if(++operations>40)throw new IllegalArgumentException();}
    private BigDecimal expression(){BigDecimal value=product();while(true){if(take('+')){operation();value=value.add(product(),CONTEXT);}else if(take('-')){operation();value=value.subtract(product(),CONTEXT);}else return value;}}
    private BigDecimal product(){BigDecimal value=number();while(true){if(take('*')){operation();value=value.multiply(number(),CONTEXT);}else if(take('/')){operation();value=value.divide(number(),CONTEXT);}else return value;}}
    private BigDecimal number(){
        if(++depth>12)throw new IllegalArgumentException();BigDecimal value;
        if(take('-'))value=number().negate();else if(take('+'))value=number();
        else if(take('(')){value=expression();if(!take(')'))throw new IllegalArgumentException();}
        else{spaces();int start=position;while(position<input.length()&&(Character.isDigit(input.charAt(position))||input.charAt(position)=='.'))position++;if(position-start>30)throw new IllegalArgumentException();value=new BigDecimal(input.substring(start,position),CONTEXT);}
        if(take('%')){operation();value=value.divide(BigDecimal.valueOf(100),CONTEXT);}depth--;return value;
    }
}
