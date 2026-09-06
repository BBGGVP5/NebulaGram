"""Exercise release selection and verifier policy; no device signature/rendering claim."""
from pathlib import Path
import importlib.util, subprocess, tempfile, sys
sys.dont_write_bytecode = True
root=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('android_version',root/'scripts/android-version.py')
versions=importlib.util.module_from_spec(spec);spec.loader.exec_module(versions)
assert versions.build_code(2)>versions.build_code(1)>70389
assert versions.app_version(root/'platform/android/version.properties')
for n in [0,-1,2147483647]:
    try:versions.build_code(n)
    except ValueError:pass
    else:raise AssertionError('Invalid build number accepted')
versions.verify_badging("package: name='app.nebulagram.messenger' versionCode='1000001' versionName='1.0.0'",'1.0.0',1000001,'app.nebulagram.messenger')
for package,code,version in [('org.telegram.messenger',1000001,'1.0.0'),('app.nebulagram.messenger',2,'1.0.0'),('app.nebulagram.messenger',1000001,'12.10.1')]:
    try:versions.verify_badging(f"package: name='{package}' versionCode='{code}' versionName='{version}'",'1.0.0',1000001,'app.nebulagram.messenger')
    except ValueError:pass
    else:raise AssertionError('Mismatched APK metadata accepted')
print('CI versions: monotonic build codes and APK/name mismatch checks passed')
stubs={
'org/telegram/tgnet/TLRPC.java':'''package org.telegram.tgnet;public class TLRPC {public static class Peer {public long channel_id;}public static class Message {public String message;public long grouped_id;public Peer peer_id=new Peer();public java.util.ArrayList<MessageEntity> entities=new java.util.ArrayList<>();}public static class MessageEntity {public int offset,length;public String url;}public static class TL_messageEntityTextUrl extends MessageEntity {}}''',
'android/content/Context.java':'''package android.content; public class Context {public java.io.File cache;public android.content.pm.PackageManager pm=new android.content.pm.PackageManager();public java.io.File getCacheDir(){return cache;}public String getPackageName(){return "app.nebulagram.messenger";}public android.content.pm.PackageManager getPackageManager(){return pm;}}''',
'android/content/pm/PackageInfo.java':'''package android.content.pm;public class PackageInfo {public String packageName,versionName;public int versionCode;public Signature[] signatures;public ApplicationInfo applicationInfo=new ApplicationInfo();}''',
'android/content/pm/PackageManager.java':'''package android.content.pm;public class PackageManager {public static final int GET_SIGNATURES=64;public PackageInfo installed,archive;public PackageInfo getPackageInfo(String p,int f){return installed;}public PackageInfo getPackageArchiveInfo(String p,int f){return archive;}}''',
'android/content/pm/Signature.java':'''package android.content.pm;public class Signature {int value;public Signature(int v){value=v;}public boolean equals(Object o){return o instanceof Signature&&((Signature)o).value==value;}public int hashCode(){return value;}}''',
'android/content/pm/ApplicationInfo.java':'''package android.content.pm;public class ApplicationInfo {public int minSdkVersion=21;}''',
'android/os/Build.java':'''package android.os;public class Build {public static String[] SUPPORTED_ABIS={"arm64-v8a","armeabi-v7a"};public static class VERSION {public static int SDK_INT=36;}}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-updates-') as temp:
    work=Path(temp);sources=[]
    for name,body in stubs.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');sources.append(str(p))
    overlay=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
    # Read Java created on Windows without carrying a BOM into javac.
    test=work/'TelegramUpdatesCheck.java';test.write_text((root/'tests/android/TelegramUpdatesCheck.java').read_text(encoding='utf-8-sig'),encoding='utf-8')
    sources += [str(overlay/'NebulaRelease.java'),str(overlay/'NebulaApkVerifier.java'),str(overlay/'NebulaChangelog.java'),str(test)]
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*sources],check=True)
    subprocess.run(['java','-cp',str(work),'app.nebulagram.ui.TelegramUpdatesCheck',str(work)],check=True)
