package com.o0ai.control.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.o0ai.control.core.ControlPolicyController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlAdminReceiver extends DeviceAdminReceiver {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Override
    public void onEnabled(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                ControlPolicyController controller = new ControlPolicyController(appContext);
                controller.ensureDebugPassword();
                controller.applyPolicy();
            } finally {
                pending.finish();
            }
        });
    }
}
