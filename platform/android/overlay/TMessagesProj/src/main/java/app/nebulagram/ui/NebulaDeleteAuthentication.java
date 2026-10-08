package app.nebulagram.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Build;
import android.widget.Toast;
import java.util.WeakHashMap;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;

/** A fresh system prompt before destructive confirmation; failed or cancelled prompts do nothing. */
public final class NebulaDeleteAuthentication {
    private static final ThreadLocal<Boolean> authorized = new ThreadLocal<>();
    private static final WeakHashMap<BaseFragment, CancellationSignal> pending = new WeakHashMap<>();
    private NebulaDeleteAuthentication() { }
    public static void cancel(BaseFragment fragment) {
        CancellationSignal signal = pending.remove(fragment);
        if (signal != null) signal.cancel();
    }
    public static boolean guard(BaseFragment fragment, Runnable confirmation) {
        if (!NebulaFeatureSettings.enabled("biometric_delete") || Boolean.TRUE.equals(authorized.get())) return false;
        return authenticate(fragment, confirmation, false);
    }
    public static void test(BaseFragment fragment) {
        authenticate(fragment, () -> Toast.makeText(fragment.getContext(), NebulaText.text("Проверка пройдена", "Authentication succeeded"), Toast.LENGTH_SHORT).show(), true);
    }
    private static boolean authenticate(BaseFragment fragment, Runnable confirmation, boolean test) {
        if (fragment == null || fragment.getParentActivity() == null) return true;
        Activity activity = fragment.getParentActivity();
        if (Build.VERSION.SDK_INT < 29) { error(activity); return true; }
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isDeviceSecure()) { error(activity); return true; }
        if (pending.containsKey(fragment)) return true;
        int account = fragment.getCurrentAccount(); long owner = UserConfig.getInstance(account).getClientUserId();
        CancellationSignal cancellation = new CancellationSignal(); pending.put(fragment, cancellation);
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                .setTitle(test ? NebulaText.text("Проверка защиты", "Test authentication") : NebulaText.text("Подтвердите удаление", "Confirm deletion"))
                .setSubtitle(NebulaText.text("Разблокируйте устройство для продолжения", "Unlock your device to continue"));
        if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(NebulaMessagePreferences.enabled("prefer_device_pin", false)
                ? BiometricManager.Authenticators.DEVICE_CREDENTIAL : BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        else builder.setDeviceCredentialAllowed(true);
        try {
            builder.build().authenticate(cancellation, AndroidUtilities::runOnUIThread, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationError(int code, CharSequence message) { pending.remove(fragment); }
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    pending.remove(fragment);
                    if (activity.isFinishing() || activity.isDestroyed() || fragment.getParentActivity() != activity
                            || owner == 0 || owner != UserConfig.getInstance(account).getClientUserId()
                            || fragment.getFragmentView() == null || !fragment.getFragmentView().isAttachedToWindow()) return;
                    authorized.set(true);
                    try { confirmation.run(); } finally { authorized.remove(); }
                }
            });
        } catch (RuntimeException exception) { pending.remove(fragment); cancellation.cancel(); error(activity); }
        return true;
    }
    private static void error(Context context) {
        Toast.makeText(context, NebulaText.text("Настройте системный пароль или биометрию устройства", "Set up a device passcode or biometrics"), Toast.LENGTH_LONG).show();
    }
}
