package app.nebulagram.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Copies to private storage before inspecting the exact file offered to Android. */
public final class NebulaApkVerifier {
    public static File prepare(Context context, File source, NebulaRelease release, long expectedSize) throws Exception {
        if (expectedSize <= 0 || expectedSize > 1500000000L || source.length() != expectedSize) throw new IOException("size");
        File directory = new File(context.getCacheDir(), "nebula-updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("directory");
        File result = new File(directory, "update-" + release.versionCode + ".apk");
        File[] previous = directory.listFiles();
        if (previous != null) for (File file : previous) {
            if (!file.equals(result) && file.isFile() && file.getName().matches("update-[0-9]+\\.apk")) file.delete();
        }
        boolean valid = false;
        try {
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(result)) {
                byte[] buffer = new byte[65536]; long copied = 0; int count;
                while ((count = in.read(buffer)) != -1) {
                    copied += count;
                    if (copied > expectedSize) throw new IOException("size");
                    out.write(buffer, 0, count);
                }
                if (copied != expectedSize) throw new IOException("size");
            }
            PackageManager pm = context.getPackageManager();
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo apk = pm.getPackageArchiveInfo(result.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            if (apk == null || !installed.packageName.equals(apk.packageName)
                    || apk.versionCode != release.versionCode || apk.versionCode <= installed.versionCode
                    || !release.versionName.equals(apk.versionName)
                    || !sameSigners(installed.signatures, apk.signatures)
                    || Build.VERSION.SDK_INT >= 24 && apk.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
                throw new IOException("package");
            }
            boolean compatible = false;
            try (ZipFile zip = new ZipFile(result)) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (zip.getEntry("lib/" + abi + "/libgojni.so") != null) compatible = true;
                }
            }
            if (!compatible) throw new IOException("abi");
            valid = true;
            return result;
        } finally {
            if (!valid) result.delete();
        }
    }

    private static boolean sameSigners(Signature[] installed, Signature[] candidate) {
        return installed != null && candidate != null && installed.length > 0
                && new HashSet<>(Arrays.asList(installed)).equals(new HashSet<>(Arrays.asList(candidate)));
    }
}
