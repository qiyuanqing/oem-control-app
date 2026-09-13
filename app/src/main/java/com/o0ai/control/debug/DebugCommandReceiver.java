package com.o0ai.control.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import com.o0ai.control.core.ControlPolicyController;
import com.o0ai.control.ui.AdminActivity;

public class DebugCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_STATUS = "com.o0ai.control.action.DEBUG_STATUS";
    public static final String ACTION_UNLOCK = "com.o0ai.control.action.DEBUG_UNLOCK";
    public static final String ACTION_LOCK = "com.o0ai.control.action.DEBUG_LOCK";
    public static final String ACTION_RESTORE = "com.o0ai.control.action.POLICY_RESTORE";
    public static final String ACTION_SETTINGS = "com.o0ai.control.action.OPEN_SETTINGS";
    public static final String ACTION_OPEN_ADMIN = "com.o0ai.control.action.OPEN_ADMIN";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (getSendingUid() != Process.SHELL_UID && getSendingUid() != Process.ROOT_UID) {
            setResultCode(1);
            setResultData("仅允许 ADB shell 调用");
            return;
        }
        ControlPolicyController controller = new ControlPolicyController(context);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STATUS.equals(action)) {
            setResultCode(0);
            setResultData(controller.statusText());
            return;
        }
        if (ACTION_OPEN_ADMIN.equals(action)) {
            if (!controller.isDeviceOwner() && !controller.hasPassword()) {
                setResultCode(2);
                setResultData("设备尚未完成初始化");
                return;
            }
            launchAdmin(context, false);
            setResultCode(0);
            setResultData("已打开设备管控");
            return;
        }
        String password = intent == null ? null : intent.getStringExtra("password");
        boolean ok;
        if (ACTION_UNLOCK.equals(action)) {
            ok = controller.unlock(password, intent.getBooleanExtra("permanent", false));
            if (ok) launchAdmin(context, false);
        } else if (ACTION_LOCK.equals(action)) {
            ok = controller.lock(password);
        } else if (ACTION_RESTORE.equals(action)) {
            ok = controller.lock(password);
        } else if (ACTION_SETTINGS.equals(action)) {
            ok = controller.unlock(password, false);
            if (ok) {
                controller.setPendingAction(ControlPolicyController.ACTION_OPEN_SETTINGS);
                launchAdmin(context, true);
            }
        } else {
            ok = false;
        }
        setResultCode(ok ? 0 : 2);
        setResultData(ok ? "操作成功" : "密码错误、模式未开启或设备尚未成为 Device Owner");
    }

    private void launchAdmin(Context context, boolean openSettings) {
        Intent intent = new Intent(context, AdminActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("open_settings", openSettings);
        context.startActivity(intent);
    }
}
