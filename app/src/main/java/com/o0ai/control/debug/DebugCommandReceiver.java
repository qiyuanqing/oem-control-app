package com.o0ai.control.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.o0ai.control.core.ControlPolicyController;
import com.o0ai.control.ui.AdminActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebugCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_STATUS = "com.o0ai.control.action.DEBUG_STATUS";
    public static final String ACTION_UNLOCK = "com.o0ai.control.action.DEBUG_UNLOCK";
    public static final String ACTION_LOCK = "com.o0ai.control.action.DEBUG_LOCK";
    public static final String ACTION_RESTORE = "com.o0ai.control.action.POLICY_RESTORE";
    public static final String ACTION_SETTINGS = "com.o0ai.control.action.OPEN_SETTINGS";
    public static final String ACTION_OPEN_ADMIN = "com.o0ai.control.action.OPEN_ADMIN";

    private static final ExecutorService WORKER = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        final Intent request = intent == null ? new Intent() : new Intent(intent);
        WORKER.execute(() -> handle(appContext, request, pending));
    }

    private void handle(Context context, Intent request, PendingResult pending) {
        ControlPolicyController controller = new ControlPolicyController(context);
        controller.ensureDebugPassword();
        String action = request.getAction();
        int code = 2;
        String data = "operation failed";
        boolean launch = false;
        boolean openSettings = false;

        try {
            if (ACTION_STATUS.equals(action)) {
                code = 0;
                data = controller.statusText();
            } else if (ACTION_OPEN_ADMIN.equals(action)) {
                if (controller.isDeviceOwner() || controller.hasPassword()) {
                    code = 0;
                    data = "admin opened";
                    launch = true;
                }
            } else {
                String password = request.getStringExtra("password");
                boolean ok;
                if (ACTION_UNLOCK.equals(action)) {
                    ok = controller.unlock(password, request.getBooleanExtra("permanent", false));
                    launch = ok;
                } else if (ACTION_LOCK.equals(action) || ACTION_RESTORE.equals(action)) {
                    ok = controller.lock(password);
                } else if (ACTION_SETTINGS.equals(action)) {
                    ok = controller.unlock(password, false);
                    if (ok) {
                        controller.setPendingAction(ControlPolicyController.ACTION_OPEN_SETTINGS);
                        launch = true;
                        openSettings = true;
                    }
                } else {
                    ok = false;
                }
                if (ok) {
                    code = 0;
                    data = "operation succeeded";
                }
            }
        } catch (RuntimeException ignored) {
            code = 2;
        }

        final int finalCode = code;
        final String finalData = data;
        final boolean finalLaunch = launch;
        final boolean finalOpenSettings = openSettings;
        MAIN.post(() -> {
            if (finalLaunch) launchAdmin(context, finalOpenSettings);
            pending.setResultCode(finalCode);
            pending.setResultData(finalData);
            pending.finish();
        });
    }

    private void launchAdmin(Context context, boolean openSettings) {
        Intent intent = new Intent(context, AdminActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("open_settings", openSettings);
        context.startActivity(intent);
    }
}
