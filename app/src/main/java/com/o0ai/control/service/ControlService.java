package com.o0ai.control.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.o0ai.control.core.ControlPolicyController;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ControlService extends Service {
    private static final String CHANNEL_ID = "device_control";
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

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
        worker.execute(() -> applyCurrentPolicy());
        return START_STICKY;
    }

    private void applyCurrentPolicy() {
        ControlPolicyController controller = new ControlPolicyController(this);
        if (!controller.isDeviceOwner()) return;
        if (controller.isPermanentMode() && controller.isDebugMode()) {
            controller.applyDebugPolicy();
        } else {
            controller.applyPolicy();
        }
        worker.schedule(this::restoreExpiredSession, 15, TimeUnit.SECONDS);
    }

    private void restoreExpiredSession() {
        ControlPolicyController controller = new ControlPolicyController(this);
        if (controller.restoreExpiredTemporarySession()) controller.applyPolicy();
        worker.schedule(this::restoreExpiredSession, 15, TimeUnit.SECONDS);
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
