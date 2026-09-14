package com.o0ai.control.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.o0ai.control.core.ControlPolicyController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                ControlPolicyController controller = new ControlPolicyController(appContext);
                controller.ensureDebugPassword();
                if (controller.isDeviceOwner()) {
                    if (controller.isPermanentMode() && controller.isDebugMode()) {
                        controller.applyDebugPolicy();
                    } else {
                        controller.applyPolicy();
                    }
                    Intent service = new Intent(appContext, com.o0ai.control.service.ControlService.class);
                    if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(service);
                    else appContext.startService(service);
                }
            } finally {
                pending.finish();
            }
        });
    }
}
