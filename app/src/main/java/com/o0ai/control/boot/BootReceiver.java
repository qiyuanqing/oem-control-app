package com.o0ai.control.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.o0ai.control.core.ControlPolicyController;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ControlPolicyController controller = new ControlPolicyController(context);
        if (controller.isDeviceOwner()) {
            if (controller.isPermanentMode() && controller.isDebugMode()) {
                controller.applyDebugPolicy();
            } else {
                controller.applyPolicy();
            }
            Intent service = new Intent(context, com.o0ai.control.service.ControlService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        }
    }
}
