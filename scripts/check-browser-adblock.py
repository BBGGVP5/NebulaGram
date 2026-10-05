"""Execute immutable host/path/cosmetic filtering against main-frame and site-isolation fixtures."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
work=root/'build/browser-adblock-check';work.mkdir(parents=True,exist_ok=True)
test=work/'BrowserCheck.java'
test.write_text(r'''import app.nebulagram.ui.NebulaAdBlockRules;import java.util.*;
public class BrowserCheck {
static void check(boolean pass,String reason){if(!pass)throw new AssertionError(reason);}
public static void main(String[] args){
String page="https://news.example.org/article";
NebulaAdBlockRules rules=NebulaAdBlockRules.parse("||ads.example.org^\n@@||ads.example.org/safe/\n||track.example.org^$third-party\n||cdn.example.org/ads/$script,domain=news.example.org|~allowed.example.org\n##.advertisement\nnews.example.org##[data-ad-slot]\n##div:style(display:none)\n##<script>bad</script>\n||ignored.example.org^$unsupported");
Set<String> none=Collections.emptySet();
check(rules.blocked("https://ads.example.org/ad.js",page,false,false,none),"ad host");
check(rules.blocked("https://sub.ads.example.org/ad.js",page,false,false,none),"subdomain");
check(!rules.blocked("https://notads.example.org/ad.js",page,false,false,none),"label boundary");
check(!rules.blocked("https://ads.example.org/safe/app.js",page,false,false,none),"allow rule");
check(!rules.blocked("https://ads.example.org/ad.js",page,true,false,none),"main frame");
check(!rules.blocked("https://ads.example.org/ad.js",page,false,true,none),"mini app");
check(!rules.blocked("https://ads.example.org/ad.js",page,false,false,Set.of("example.org")),"site exception");
check(rules.blocked("https://track.example.org/a",page,false,false,none),"third party");
check(!rules.blocked("https://track.example.org/a","https://track.example.org/page",false,false,none),"first party");
check(rules.blocked("https://cdn.example.org/ads/a.js",page,false,false,none),"path/domain/script");
check(!rules.blocked("https://cdn.example.org/ads/a.css",page,false,false,none),"resource type");
check(!rules.blocked("https://cdn.example.org/ads/a.js","https://allowed.example.org/",false,false,none),"excluded rule domain");
check(!rules.blocked("https://ignored.example.org/a",page,false,false,none),"unsupported option");
check(rules.css(page,none).contains("[data-ad-slot]{display:none!important;}"),"scoped cosmetic");
check(!rules.css("https://other.example.org/",none).contains("[data-ad-slot]"),"cosmetic site isolation");
check(rules.css(page,Set.of("news.example.org")).isEmpty(),"cosmetic whitelist");
check(!rules.css(page,none).contains("script")&&!rules.css(page,none).contains(":style"),"CSS only");
check(NebulaAdBlockRules.domain("https://example.org").isEmpty()&&NebulaAdBlockRules.domain("../x").isEmpty(),"exceptions are domains");
StringBuilder many=new StringBuilder();for(int i=0;i<1000;i++)many.append("##.ad").append(i).append('\n');
check(NebulaAdBlockRules.parse(many.toString()).css(page,none).length()<32000,"CSS bound");
try{NebulaAdBlockRules.parse("x".repeat(8_000_001));throw new AssertionError("filter bound");}catch(IllegalArgumentException ok){}
System.out.println("Browser adblock: host boundaries, allow rules, page/mini-app exclusions, third-party/path/type rules and bounded CSS passed");}}
''',encoding='utf-8')
source=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAdBlockRules.java'
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(test),str(source)],check=True)
subprocess.run(['java','-cp',str(work),'BrowserCheck'],check=True)
