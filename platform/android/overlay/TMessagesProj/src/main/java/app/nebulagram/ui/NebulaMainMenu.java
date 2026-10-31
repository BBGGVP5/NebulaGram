package app.nebulagram.ui;

import android.os.Bundle;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.QrShare.QrShareActivity;
import org.telegram.ui.Components.ItemOptions;

/** Main-screen destinations that remain reachable with hidden bottom tabs. */
public final class NebulaMainMenu {
    private NebulaMainMenu() { }

    public static void addChatActions(BaseFragment host, ItemOptions menu) {
        menu.add(R.drawable.msg_channel, LocaleController.getString(R.string.NewChannel), () ->
                host.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE)));
        menu.add(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats), () -> {
            Bundle args = new Bundle();
            args.putInt("folderId", 1);
            host.presentFragment(new DialogsActivity(args));
        });
    }

    public static void addNavigation(BaseFragment host, ItemOptions menu) {
        menu.add(R.drawable.msg_calls, LocaleController.getString(R.string.Calls), () -> {
            Bundle args = new Bundle();
            args.putBoolean("needFinishFragment", false);
            args.putBoolean("hasMainTabs", false);
            host.presentFragment(new CallLogActivity(args));
        });
        NebulaMenuDivider.add(menu, host.getParentActivity(), host.getResourceProvider());
        menu.add(new NebulaQrIcon(true), LocaleController.getString(R.string.NebulaScanQr), () -> {
            android.app.Activity activity = host.getParentActivity();
            if (activity == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && activity.checkSelfPermission(android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.CAMERA},
                        ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }
            CameraScanActivity.showAsSheet(host, true, CameraScanActivity.TYPE_QR,
                        new CameraScanActivity.CameraScanActivityDelegate() {
                            @Override
                            public void didFindQr(String text) {
                                Browser.openUrl(host.getParentActivity(), text);
                            }
                        });
        });
        // 13.1.1 заменил QrActivity на сцену QrShare с собственной фабрикой
        // для своего кода: она сама подставляет текущий аккаунт.
        menu.add(new NebulaQrIcon(false), LocaleController.getString(R.string.NebulaMyQr), () ->
                host.presentFragment(QrShareActivity.self(host.getCurrentAccount())));
    }
}
