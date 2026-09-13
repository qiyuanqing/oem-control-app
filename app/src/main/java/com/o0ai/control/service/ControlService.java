package com.o0ai.control.service;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import com.o0ai.control.core.ControlPolicyController;

public class ControlService extends Service {
    private static final String CHANNEL_ID = "device_control";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable policyCheck = new Runnable() {
        @Override
        public void run() {
            ControlPolicyController controller = new ControlPolicyController(ControlService.this);
            if (controller.restoreExpiredTemporarySession()) {
                controller.applyPolicy();
            }
            handler.postDelayed(this, 15_000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "设备管控服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            startForeground(1001, new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("设备管控")
                    .setContentText("设备策略正在运行")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setOngoing(true)
                    .build());
        }
        ControlPolicyController controller = new ControlPolicyController(this);
        controller.tryAutoProvisionDeviceOwner();
        if (controller.isDeviceOwner()) {
            if (controller.isPermanentMode() && controller.isDebugMode()) {
                controller.applyDebugPolicy();
            } else {
                controller.applyPolicy();
            }
        }
        handler.removeCallbacks(policyCheck);
        handler.post(policyCheck);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(policyCheck);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
