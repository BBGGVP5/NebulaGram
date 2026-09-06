package app.nebulagram.ui;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import android.content.*;
import android.content.pm.*;
import org.telegram.tgnet.TLRPC;

public class TelegramUpdatesCheck {
 static int checks;
 static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
 static NebulaRelease release(int code,String abi){return NebulaRelease.parse("NebulaGram-1.0.0-TG-12.10.1-b"+code+"-"+abi+".apk");}
 static TLRPC.Message caption(String text,long group){TLRPC.Message m=new TLRPC.Message();m.message=text;m.grouped_id=group;m.peer_id.channel_id=NebulaRelease.CHANNEL_ID;return m;}
 static void rejected(Context context,File apk,NebulaRelease release,long size,String why)throws Exception{
  boolean rejected=false;try{NebulaApkVerifier.prepare(context,apk,release,size);}catch(Exception expected){rejected=true;}
  check(rejected,why);
  check(!new File(context.cache,"nebula-updates/update-1000002.apk").exists(),"Invalid private APK retained");
 }
 public static void main(String[] args)throws Exception{
  String[] arm64={"arm64-v8a","armeabi-v7a"};
  check(NebulaRelease.isUpdateLink("tg://update") && NebulaRelease.isUpdateLink("TG://UPDATE/"),"Update deep link rejected");
  check(!NebulaRelease.isUpdateLink("tg://update?channel=other") && !NebulaRelease.isUpdateLink(null),"Unrecognized update link accepted");
  String post="https://t.me/"+NebulaRelease.CHANNEL+"/42";
  check(NebulaRelease.linkedPost(post)==42 && NebulaRelease.linkedPost(post+"?single")==42,"Exact release post rejected");
  for(String bad:new String[]{"https://t.me/ngram_official/42",post+"/5","https://t.me.evil/"+NebulaRelease.CHANNEL+"/42","http://t.me/"+NebulaRelease.CHANNEL+"/42",post.replace("/42","/2147483648"),post.replace("t.me/","evil@t.me/"),null})check(NebulaRelease.linkedPost(bad)==0,"Foreign or invalid changelog accepted");
  check(NebulaRelease.plainChangelogPost("✨ Что нового: "+post)==42,"Russian release reference lost");
  check(NebulaRelease.plainChangelogPost("🇺🇸 Release notes: "+post)==42,"English release reference lost");
  check(NebulaRelease.plainChangelogPost("📰 Новости: "+post)==0,"News mistaken for changelog");
  check(NebulaRelease.plainChangelogPost("Что нового: https://t.me/ngram_official/42")==0,"News channel used for changes");
  TLRPC.Message apkPost=caption("arm64-v8a",5),notes=caption("🇺🇸 New version\n🇷🇺 Что нового\n✨ Исправления\nمرحبا",5);
  TLRPC.Message noise=caption("News unrelated to the release "+new String(new char[300]).replace('\0','x'),6);
  check(NebulaChangelog.albumCaption(apkPost,java.util.Arrays.asList(noise,notes))==notes,"Album caption not associated by group");
  notes.peer_id.channel_id=1;
  check(NebulaChangelog.albumCaption(apkPost,java.util.Arrays.asList(noise,notes))==apkPost,"Foreign channel album accepted");
  notes.peer_id.channel_id=NebulaRelease.CHANNEL_ID;
  TLRPC.Message linked=caption("✨ Что нового",5);
  TLRPC.TL_messageEntityTextUrl link=new TLRPC.TL_messageEntityTextUrl();link.offset=linked.message.indexOf("Что");link.length="Что нового".length();link.url=post;linked.entities.add(link);
  check(NebulaChangelog.reference(linked)==42,"Rich text reference with emoji prefix lost");
  check(NebulaChangelog.albumCaption(apkPost,java.util.Arrays.asList(notes,linked))==linked,"Explicit link lost to longer caption");
  link.offset=Integer.MAX_VALUE;
  check(NebulaChangelog.reference(linked)==0,"Overflowing entity range accepted");
  String multilingual="🫧 🇷🇺 English Русский العربية";
  check(NebulaChangelog.validRange(0,2,multilingual.length()),"UTF-16 emoji span rejected");
  for(int[] invalid:new int[][]{{-1,2},{0,0},{1,Integer.MAX_VALUE},{Integer.MAX_VALUE,1},{multilingual.length(),1}})check(!NebulaChangelog.validRange(invalid[0],invalid[1],multilingual.length()),"Invalid entity range accepted");
  NebulaRelease arm=release(1000002,"arm64-v8a"),universal=release(1000002,"universal"),arm32=release(1000002,"armeabi-v7a");
  check(arm!=null&&arm.versionName.equals("1.0.0")&&arm.telegramVersion.equals("12.10.1"),"Independent versions lost");
  check(arm.compatible(arm64)&&!arm.compatible(new String[]{"x86"}),"Wrong architecture offered");
  check(universal.compatible(new String[]{"x86_64"}),"Universal APK rejected");
  check(arm.preferredTo(universal,arm64)&&universal.preferredTo(arm32,arm64),"Architecture ranking");
  check(!arm.preferredTo(release(1000002,"arm64-v8a"),arm64),"Reupload treated as new build");
  check(release(1000003,"universal").preferredTo(arm,arm64),"Same-version fixed rebuild ignored");
  check(!release(1000001,"arm64-v8a").preferredTo(arm,arm64),"Reposted old APK wins");
  for(String bad:new String[]{null,"app.apk","NebulaGram-1.0.0-TG-12.10.1-b0-universal.apk","NebulaGram-1.0.0-TG-12.10.1-b2147483648-universal.apk","NebulaGram-Beta-1.0.0-TG-12.10.1-b1000002-universal.apk","../NebulaGram-1.0.0-TG-12.10.1-b1000002-universal.apk","NebulaGram-1.0.0-TG-12.10.1-b1000002-mips.apk","NebulaGram-1.0.0-TG-12.10.1-b1000002-arm64-v8a.apk.exe"})check(NebulaRelease.parse(bad)==null,"Bad filename accepted: "+bad);
  Context c=new Context();c.cache=new File(args[0],"private");c.cache.mkdirs();
  File apk=new File(args[0],"source.apk");
  try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(apk))){zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libgojni.so"));zip.write(new byte[]{1,2,3});zip.closeEntry();}
  long size=apk.length();c.pm.installed=info(1000001);c.pm.archive=info(1000002);
  File result=NebulaApkVerifier.prepare(c,apk,arm,size);
  check(result.isFile()&&java.util.Arrays.equals(Files.readAllBytes(apk.toPath()),Files.readAllBytes(result.toPath())),"Private copy differs");
  c.pm.archive.packageName="org.telegram.messenger";rejected(c,apk,arm,size,"Wrong package accepted");
  c.pm.archive=info(1000002);c.pm.archive.versionName="2.0.0";rejected(c,apk,arm,size,"Misnamed version accepted");
  c.pm.archive=info(1000001);rejected(c,apk,arm,size,"Old APK accepted");
  c.pm.archive=info(1000003);rejected(c,apk,arm,size,"Filename/code mismatch accepted");
  c.pm.archive=info(1000002);c.pm.archive.signatures=new Signature[]{new Signature(2)};rejected(c,apk,arm,size,"Other signer accepted");
  c.pm.archive=info(1000002);c.pm.archive.signatures=null;rejected(c,apk,arm,size,"Unsigned APK accepted");
  c.pm.archive=info(1000002);c.pm.archive.applicationInfo.minSdkVersion=99;rejected(c,apk,arm,size,"Incompatible SDK accepted");
  c.pm.archive=info(1000002);android.os.Build.SUPPORTED_ABIS=new String[]{"x86"};rejected(c,apk,arm,size,"Actual wrong ABI accepted");
  android.os.Build.SUPPORTED_ABIS=arm64;rejected(c,apk,arm,size+1,"Truncated download accepted");
  c.pm.archive=null;rejected(c,apk,arm,size,"Malformed APK accepted");
  check(apk.isFile(),"Original Telegram file removed by verification");
  System.out.println(checks+" release/reupload/ABI and APK validation checks passed (package parsing is mocked)");
 }
 static PackageInfo info(int code){PackageInfo p=new PackageInfo();p.packageName="app.nebulagram.messenger";p.versionName="1.0.0";p.versionCode=code;p.signatures=new Signature[]{new Signature(1)};return p;}
}
