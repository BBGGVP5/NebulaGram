package app.nebulagram.ui;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.telegram.messenger.ApplicationLoader;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** Keys are encrypted at rest and excluded from Android backup and settings export. */
public final class NebulaAiSecrets {
    private static final String ALIAS = "nebulagram.ai.credentials";
    private NebulaAiSecrets() { }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private static AtomicFile file(int provider) {
        if (provider < 0 || provider > 3) throw new IllegalArgumentException();
        return new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "ai-key-" + provider));
    }
    public static synchronized void save(int provider, String value) throws Exception {
        AtomicFile file = file(provider);
        if (value.isEmpty()) { file.delete(); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        FileOutputStream out = file.startWrite();
        try { out.write(cipher.getIV().length); out.write(cipher.getIV()); out.write(encrypted); file.finishWrite(out); }
        catch (Exception e) { file.failWrite(out); throw e; }
    }
    public static synchronized String read(int provider) throws Exception {
        AtomicFile file = file(provider);
        if (!file.getBaseFile().exists()) return "";
        byte[] bytes = file.readFully();
        int count = bytes[0] & 255;
        if (count != 12 || bytes.length < count + 17) throw new IOException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 1, count));
        return new String(cipher.doFinal(bytes, count + 1, bytes.length - count - 1), StandardCharsets.UTF_8);
    }
}
